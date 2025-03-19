// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.*;
import org.jetbrains.jps.dependency.diff.Difference;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.jetbrains.jps.javac.Iterators.*;

/**
 * This class provides implementation common to all jvm strategies
 */
public abstract class JvmDifferentiateStrategyImpl implements JvmDifferentiateStrategy{
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.dependency.java.JvmDifferentiateStrategyImpl");

  protected enum AnnotationAffectionKind {
    added, removed, changed
  }
  protected final <T extends AnnotationInstance, D extends AnnotationInstance.Diff<T>> boolean isAffectedByAnnotations(
    Proto element, Difference.Specifier<T, D> annotationsDiff, Set<AnnotationAffectionKind> affectionKinds, Predicate<? super TypeRepr.ClassType> annotationSelector
  ) {
    return !element.isPrivate() && find(getAffectedAnnotations(annotationsDiff, affectionKinds), annotationSelector::test) != null;
  }

  protected final <T extends AnnotationInstance, D extends AnnotationInstance.Diff<T>> Iterable<TypeRepr.ClassType> getAffectedAnnotations(
    Difference.Specifier<T, D> annotationsDiff, Set<AnnotationAffectionKind> affectionKinds
  ) {
    Iterable<? extends T> added = affectionKinds.contains(AnnotationAffectionKind.added)? annotationsDiff.added() : List.of();
    Iterable<? extends T> removed = affectionKinds.contains(AnnotationAffectionKind.removed)? annotationsDiff.removed() : List.of();
    Iterable<? extends T> changed = affectionKinds.contains(AnnotationAffectionKind.changed)? map(annotationsDiff.changed(), Difference.Change::getPast) : List.of();
    return map(flat(List.of(added, removed, changed)), AnnotationInstance::getAnnotationClass);
  }

  protected enum AnnotationAffectionScope {
    /**
     * If present in the returned result set, the usages of the annotated program element (class, field, method) will be affected.
     * it means that files where this program element is references, will be marked for recompilation
     */
    usages,

    /**
     * If present in the returned result set, the subclasses of the annotated class will be affected.
     * If returned for an annotated field/method, the subclasses of the class containing this field/method will be affected.
     */
    subclasses
  }

  protected void affectClassAnnotationUsages(DifferentiateContext context, Set<AnnotationAffectionScope> toRecompile, Difference.Change<JvmClass, JvmClass.Diff> change, Utils future, Utils present) {
    JvmClass changedClass = change.getPast();
    boolean affectUsages = toRecompile.contains(AnnotationAffectionScope.usages);
    if (affectUsages) {
      context.affectUsage(new ClassUsage(changedClass.getReferenceID()));
    }
    if (toRecompile.contains(AnnotationAffectionScope.subclasses)) {
      affectSubclasses(context, future, changedClass.getReferenceID(), affectUsages);
    }
  }

  protected void affectFieldAnnotationUsages(DifferentiateContext context, Set<AnnotationAffectionScope> toRecompile, Difference.Change<JvmClass, JvmClass.Diff> clsChange, JvmField changedField, Utils future, Utils present) {
    JvmClass changedClass = clsChange.getPast();
    if (toRecompile.contains(AnnotationAffectionScope.usages)) {
      affectMemberUsages(context, changedClass.getReferenceID(), changedField, future.collectSubclassesWithoutField(changedClass.getReferenceID(), changedField));
    }
    if (toRecompile.contains(AnnotationAffectionScope.subclasses)) {
      affectSubclasses(context, future, changedClass.getReferenceID(), false);
    }
  }

  protected void affectMethodAnnotationUsages(DifferentiateContext context, Set<AnnotationAffectionScope> toRecompile, Difference.Change<JvmClass, JvmClass.Diff> clsChange, JvmMethod changedMethod, Utils future, Utils present) {
    JvmClass changedClass = clsChange.getPast();
    if (toRecompile.contains(AnnotationAffectionScope.usages)) {
      affectMemberUsages(context, changedClass.getReferenceID(), changedMethod, future.collectSubclassesWithoutMethod(changedClass.getReferenceID(), changedMethod));
      if (changedMethod.isAbstract() || toRecompile.contains(AnnotationAffectionScope.subclasses)) {
        for (Pair<JvmClass, JvmMethod> pair : recurse(Pair.create(changedClass, changedMethod), p -> p.second.isOverridable()? future.getOverridingMethods(p.first, p.second, p.second::isSameByJavaRules) : Collections.emptyList(), false)) {
          JvmNodeReferenceID clsId = pair.first.getReferenceID();
          JvmMethod meth = pair.getSecond();
          affectMemberUsages(context, clsId, meth, future.collectSubclassesWithoutMethod(clsId, meth));
        }
      }
    }
    if (toRecompile.contains(AnnotationAffectionScope.subclasses)) {
      affectSubclasses(context, future, changedClass.getReferenceID(), false);
    }
  }

  protected void affectMemberUsages(DifferentiateContext context, JvmNodeReferenceID clsId, ProtoMember member, Iterable<JvmNodeReferenceID> propagated) {
    affectMemberUsages(context, clsId, member, propagated, null);
  }

  protected void affectMemberUsages(DifferentiateContext context, JvmNodeReferenceID clsId, ProtoMember member, Iterable<JvmNodeReferenceID> propagated, @Nullable Predicate<Node<?, ?>> constraint) {
    affectUsages(
      context, (member instanceof JvmMethod? "method " : member instanceof JvmField? "field " : "member ") + member,
      flat(asIterable(clsId), propagated),
      id -> member.createUsage(id),
      constraint
    );
  }

  protected void affectStaticMemberOnDemandUsages(DifferentiateContext context, JvmNodeReferenceID clsId, Iterable<JvmNodeReferenceID> propagated) {
    affectUsages(
      context, "static member on-demand import usage",
      flat(asIterable(clsId), propagated),
      id -> new ImportStaticOnDemandUsage(id),
      null
    );
  }

  protected void affectStaticMemberImportUsages(DifferentiateContext context, JvmNodeReferenceID clsId, String memberName, Iterable<JvmNodeReferenceID> propagated) {
    affectUsages(
      context, "static member import",
      flat(asIterable(clsId), propagated),
      id -> new ImportStaticMemberUsage(id.getNodeName(), memberName),
      null
    );
  }

  protected void affectUsages(DifferentiateContext context, String usageKind, Iterable<JvmNodeReferenceID> usageOwners, Function<? super JvmNodeReferenceID, ? extends Usage> usageFactory, @Nullable Predicate<Node<?, ?>> constraint) {
    for (JvmNodeReferenceID id : usageOwners) {
      if (constraint != null) {
        context.affectUsage(usageFactory.apply(id), constraint);
      }
      else {
        context.affectUsage(usageFactory.apply(id));
      }
      debug("Affect ", usageKind, " usage owned by node '", id.getNodeName(), "'");
    }
  }

  protected boolean isDebugEnabled() {
    return LOG.isDebugEnabled();
  }

  protected void debug(String message, Object... details) {
    if (isDebugEnabled()) {
      StringBuilder msg = new StringBuilder(message);
      for (Object detail : details) {
        msg.append(detail);
      }
      debug(msg.toString());
    }
  }

  protected void debug(String message) {
    LOG.debug(message);
  }

  protected void affectSubclasses(DifferentiateContext context, Utils utils, ReferenceID fromClass, boolean affectUsages) {
    debug("Affecting subclasses of class: ", fromClass, "; with usages affection: ", affectUsages);
    for (ReferenceID cl : utils.withAllSubclasses(fromClass)) {
      affectNodeSources(context, cl, "Affecting source file: ", utils);
      if (affectUsages) {
        String nodeName = utils.getNodeName(cl);
        if (nodeName != null) {
          context.affectUsage(new ClassUsage(nodeName));
          debug("Affect usage of class ", nodeName);
        }
      }
    }
  }

  protected void affectNodeSources(DifferentiateContext context, ReferenceID clsId, String affectReason, Utils utils) {
    affectSources(context, utils.getNodeSources(clsId), affectReason, false);
  }

  protected void affectSources(DifferentiateContext context, Iterable<NodeSource> sources, String affectReason, boolean forceAffect) {
    Set<NodeSource> deletedSources = context.getDelta().getDeletedSources();
    Predicate<? super NodeSource> affectionFilter = context.getParams().affectionFilter();
    for (NodeSource source : filter(sources, affectionFilter::test)) {
      if ((forceAffect || !context.isCompiled(source)) && !deletedSources.contains(source)) {
        context.affectNodeSource(source);
        debug(affectReason, source);
      }
    }
  }
}
