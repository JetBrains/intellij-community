// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.*;

import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.jetbrains.jps.javac.Iterators.*;

/**
 * This class provides some common utilities for strategy implementations
 */
public abstract class JvmDifferentiateStrategyImpl implements JvmDifferentiateStrategy {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.dependency.java.JvmDifferentiateStrategyImpl");

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
