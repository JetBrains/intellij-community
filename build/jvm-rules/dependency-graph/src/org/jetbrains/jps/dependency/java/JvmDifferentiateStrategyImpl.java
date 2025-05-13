// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.*;
import org.jetbrains.jps.dependency.diff.Difference;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.jetbrains.jps.javac.Iterators.*;

/**
 * This class provides implementation common to all jvm strategies
 */
public abstract class JvmDifferentiateStrategyImpl implements JvmDifferentiateStrategy{
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.dependency.java.JvmDifferentiateStrategyImpl");

  protected final <T extends AnnotationInstance, D extends AnnotationInstance.Diff<T>> boolean isAffectedByAnnotations(
    Proto element, Difference.Specifier<T, D> annotationsDiff, Set<AnnotationGroup.AffectionKind> affectionKinds, Predicate<? super TypeRepr.ClassType> annotationSelector
  ) {
    return !element.isPrivate() && find(getAffectedAnnotations(annotationsDiff, affectionKinds), annotationSelector::test) != null;
  }

  protected final <T extends AnnotationInstance, D extends AnnotationInstance.Diff<T>> Iterable<TypeRepr.ClassType> getAffectedAnnotations(
    Difference.Specifier<T, D> annotationsDiff, Set<AnnotationGroup.AffectionKind> affectionKinds
  ) {
    Iterable<? extends T> added = affectionKinds.contains(AnnotationGroup.AffectionKind.added)? annotationsDiff.added() : List.of();
    Iterable<? extends T> removed = affectionKinds.contains(AnnotationGroup.AffectionKind.removed)? annotationsDiff.removed() : List.of();
    Iterable<? extends T> changed = affectionKinds.contains(AnnotationGroup.AffectionKind.changed)? map(annotationsDiff.changed(), Difference.Change::getPast) : List.of();
    return map(flat(List.of(added, removed, changed)), AnnotationInstance::getAnnotationClass);
  }

  protected Iterable<AnnotationGroup> getTrackedAnnotations() {
    return Collections.emptyList();
  }

  @Override
  public boolean isAnnotationTracked(TypeRepr.@NotNull ClassType annotationType) {
    return find(getTrackedAnnotations(), gr -> gr.types.contains(annotationType)) != null;
  }

  private Set<AnnotationGroup.AffectionScope> getMaxPossibleScope() {
    Set<AnnotationGroup.AffectionScope> result = EnumSet.noneOf(AnnotationGroup.AffectionScope.class);
    for (AnnotationGroup group : getTrackedAnnotations()) {
      result.addAll(group.affectionScope);
    }
    return result;
  }
  
  @Override
  public boolean processClassAnnotations(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> change, Difference.Specifier<ElementAnnotation, ElementAnnotation.Diff> annotationDiff, Utils future, Utils present) {
    Set<AnnotationGroup.AffectionScope> maxScope = getMaxPossibleScope();
    if (maxScope.isEmpty()) {
      return true;
    }
    JvmClass changedClass = change.getPast();
    Set<AnnotationGroup.AffectionScope> affectionScope = EnumSet.noneOf(AnnotationGroup.AffectionScope.class);
    for (AnnotationGroup group : filter(getTrackedAnnotations(), gr -> gr.targets.contains(AnnotationGroup.AnnTarget.type))) {
      if (isAffectedByAnnotations(changedClass, annotationDiff, group.affectionKind, group.types::contains)) {
        debug(group.name, " changed for ", changedClass.getName(), " --- affecting class usages");
        affectionScope.addAll(group.affectionScope);
        if (affectionScope.equals(maxScope)) {
          break;
        }
      }
    }

    if (!affectionScope.isEmpty()) {
      affectClassAnnotationUsages(context, affectionScope, change, future, present);
    }

    return true;
  }

  @Override
  public boolean processFieldAnnotations(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> clsChange, Difference.Change<JvmField, JvmField.Diff> fieldChange, Difference.Specifier<ElementAnnotation, ElementAnnotation.Diff> annotationDiff, Utils future, Utils present) {
    Set<AnnotationGroup.AffectionScope> maxScope = getMaxPossibleScope();
    if (maxScope.isEmpty()) {
      return true;
    }
    JvmField changedField = fieldChange.getPast();
    Set<AnnotationGroup.AffectionScope> affectionScope = EnumSet.noneOf(AnnotationGroup.AffectionScope.class);
    for (AnnotationGroup group : filter(getTrackedAnnotations(), gr -> gr.targets.contains(AnnotationGroup.AnnTarget.field))) {
      if (isAffectedByAnnotations(changedField, annotationDiff, group.affectionKind, group.types::contains) ) {
        debug(group.name, " changed for field ", changedField, " --- affecting field usages");
        affectionScope.addAll(group.affectionScope);
        if (affectionScope.equals(maxScope)) {
          break;
        }
      }
    }

    if (!affectionScope.isEmpty()) {
      affectFieldAnnotationUsages(context, affectionScope, clsChange, changedField, future, present);
    }
    return true;
  }

  @Override
  public boolean processMethodAnnotations(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> clsChange, Difference.Change<JvmMethod, JvmMethod.Diff> methodChange, Difference.Specifier<ElementAnnotation, ElementAnnotation.Diff> annotationsDiff, Difference.Specifier<ParamAnnotation, ParamAnnotation.Diff> paramAnnotationsDiff, Utils future, Utils present) {
    Set<AnnotationGroup.AffectionScope> maxScope = getMaxPossibleScope();
    JvmMethod changedMethod = methodChange.getPast();
    if (!changedMethod.isFinal()) {
      maxScope.add(AnnotationGroup.AffectionScope.subclasses);
    }

    Set<AnnotationGroup.AffectionScope> affectionScope = EnumSet.noneOf(AnnotationGroup.AffectionScope.class);

    for (AnnotationGroup group : filter(getTrackedAnnotations(), gr -> gr.targets.contains(AnnotationGroup.AnnTarget.method))) {
      if (isAffectedByAnnotations(changedMethod, annotationsDiff, group.affectionKind, group.types::contains)) {
        affectionScope.addAll(group.affectionScope);
        if (!changedMethod.isFinal()) {
          // ensure the affection scope is expanded for subclasses
          affectionScope.add(AnnotationGroup.AffectionScope.subclasses);
          debug(group.name, " changed for non-final method ", changedMethod, " --- affecting method usages and subclasses");
        }
        else {
          debug(group.name, " changed for method ", changedMethod, " --- affecting method usages");
        }
        if (affectionScope.equals(maxScope)) {
          break;
        }
      }
    }

    if (!affectionScope.equals(maxScope)) {
      for (AnnotationGroup group : filter(getTrackedAnnotations(), gr -> gr.targets.contains(AnnotationGroup.AnnTarget.method_parameter))) {
        if (isAffectedByAnnotations(changedMethod, paramAnnotationsDiff, group.affectionKind, group.types::contains)) {
          affectionScope.addAll(group.affectionScope);
          if (!changedMethod.isFinal()) {
            // ensure the affection scope is expanded for subclasses
            affectionScope.add(AnnotationGroup.AffectionScope.subclasses);
            debug(group.name, " changed for non-final method parameters ", changedMethod, " --- affecting method usages and subclasses");
          }
          else {
            debug(group.name, " changed for method parameters ", changedMethod, " --- affecting method usages");
          }
          if (affectionScope.equals(maxScope)) {
            break;
          }
        }
      }
    }

    if (!affectionScope.isEmpty()) {
      affectMethodAnnotationUsages(context, affectionScope, clsChange, changedMethod, future, present);
    }
    return true;
  }

  protected void affectClassAnnotationUsages(DifferentiateContext context, Set<AnnotationGroup.AffectionScope> toRecompile, Difference.Change<JvmClass, JvmClass.Diff> change, Utils future, Utils present) {
    JvmClass changedClass = change.getPast();
    boolean affectUsages = toRecompile.contains(AnnotationGroup.AffectionScope.usages);
    if (affectUsages) {
      context.affectUsage(new ClassUsage(changedClass.getReferenceID()));
    }
    if (toRecompile.contains(AnnotationGroup.AffectionScope.subclasses)) {
      affectSubclasses(context, future, changedClass.getReferenceID(), affectUsages);
    }
  }

  protected void affectFieldAnnotationUsages(DifferentiateContext context, Set<AnnotationGroup.AffectionScope> toRecompile, Difference.Change<JvmClass, JvmClass.Diff> clsChange, JvmField changedField, Utils future, Utils present) {
    JvmClass changedClass = clsChange.getPast();
    if (toRecompile.contains(AnnotationGroup.AffectionScope.usages)) {
      affectMemberUsages(context, changedClass.getReferenceID(), changedField, future.collectSubclassesWithoutField(changedClass.getReferenceID(), changedField));
    }
    if (toRecompile.contains(AnnotationGroup.AffectionScope.subclasses)) {
      affectSubclasses(context, future, changedClass.getReferenceID(), false);
    }
  }

  protected void affectMethodAnnotationUsages(DifferentiateContext context, Set<AnnotationGroup.AffectionScope> toRecompile, Difference.Change<JvmClass, JvmClass.Diff> clsChange, JvmMethod changedMethod, Utils future, Utils present) {
    JvmClass changedClass = clsChange.getPast();
    if (toRecompile.contains(AnnotationGroup.AffectionScope.usages)) {
      affectMemberUsages(context, changedClass.getReferenceID(), changedMethod, future.collectSubclassesWithoutMethod(changedClass.getReferenceID(), changedMethod));
      if (changedMethod.isAbstract() || toRecompile.contains(AnnotationGroup.AffectionScope.subclasses)) {
        for (Pair<JvmClass, JvmMethod> pair : recurse(Pair.create(changedClass, changedMethod), p -> p.second.isOverridable()? future.getOverridingMethods(p.first, p.second, p.second::isSameByJavaRules) : Collections.emptyList(), false)) {
          JvmNodeReferenceID clsId = pair.first.getReferenceID();
          JvmMethod meth = pair.getSecond();
          affectMemberUsages(context, clsId, meth, future.collectSubclassesWithoutMethod(clsId, meth));
        }
      }
    }
    if (toRecompile.contains(AnnotationGroup.AffectionScope.subclasses)) {
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

  // affects node sources in the current chunk
  protected boolean affectNodeSourcesIfNotCompiled(DifferentiateContext context, Iterable<ReferenceID> nodeIds, Utils utils, String affectReason) {
    Set<NodeSource> deletedSources = context.getDelta().getDeletedSources();
    Predicate<? super NodeSource> belongsToChunk = context.getParams().belongsToCurrentCompilationChunk();

    Set<NodeSource> candidates = collect(
      filter(flat(map(nodeIds, utils::getNodeSources)), s -> !deletedSources.contains(s) && belongsToChunk.test(s)), new HashSet<>()
    );

    if (find(candidates, src -> !context.isCompiled(src)) != null) { // contains non-compiled sources
      final StringBuilder msg = new StringBuilder(affectReason);
      for (NodeSource candidate : candidates) {
        context.affectNodeSource(candidate);
        msg.append(candidate).append("; ");
      }
      debug(msg.toString());
      return true;
    }
    return false;
  }
}
