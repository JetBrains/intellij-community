// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.jps.dependency.*;
import org.jetbrains.jps.dependency.diff.Difference;
import org.jetbrains.jps.dependency.impl.NodeDependenciesIndex;
import org.jetbrains.jps.javac.Iterators;

import java.util.function.Consumer;

public class JavaDifferentiateStrategy implements DifferentiateStrategy {

  @Override
  public void differentiate(DifferentiateContext context, Iterable<Node<?, ?>> nodesBefore, Iterable<Node<?, ?>> nodesAfter) {
    Utils future = new Utils(context.getGraph(), context.getDelta());
    Utils present = new Utils(context.getGraph(), null);

    Difference.Specifier<JvmClass, JvmClass.Diff> classesDiff = Difference.deepDiff(
      Graph.getNodesOfType(nodesBefore, JvmClass.class), Graph.getNodesOfType(nodesAfter, JvmClass.class)
    );
    for (JvmClass removed : classesDiff.removed()) {

    }
    for (JvmClass added : classesDiff.added()) {

    }
    for (Difference.Change<JvmClass, JvmClass.Diff> change : classesDiff.changed()) {
      JvmClass changedClass = change.getPast();
      JvmClass.Diff classDiff = change.getDiff();
      if (classDiff.superClassChanged() || classDiff.signatureChanged() || !classDiff.interfaces().unchanged()) {
        boolean extendsChanged = classDiff.superClassChanged() && !classDiff.extendsAdded();
        boolean affectUsages = classDiff.signatureChanged() || extendsChanged || !Iterators.isEmpty(classDiff.interfaces().removed());
        affectSubclasses(context, future, change.getNow().getReferenceID(), affectUsages);

        if (extendsChanged) {
          TypeRepr.ClassType exClass = new TypeRepr.ClassType(changedClass.getName());
          for (ReferenceID dep : context.getGraph().getIndex(NodeDependenciesIndex.NAME).getDependencies(changedClass.getReferenceID())) {
            for (JvmClass depClass : present.getNodes(dep, JvmClass.class)) {
              for (JvmMethod method : depClass.getMethods()) {
                if (method.getExceptions().contains(exClass)) {
                  context.affectUsage(method.createUsage(depClass.getName()));
                  debug("Affecting usages of methods throwing " + exClass.getJvmName() + " exception; class " + depClass.getName());
                }
              }
            }
          }
        }

        if (!changedClass.isAnonymous()) {
          //
        }
      }
    }

    Difference.Specifier<JvmModule, JvmModule.Diff> modulesDiff = Difference.deepDiff(
      Graph.getNodesOfType(nodesBefore, JvmModule.class), Graph.getNodesOfType(nodesAfter, JvmModule.class)
    );
    for (JvmModule removed : modulesDiff.removed()) {

    }
    for (JvmModule added : modulesDiff.added()) {

    }
    for (var change : modulesDiff.changed()) {
      JvmModule.Diff moduleDiff = change.getDiff();
      
    }
  }

  private void affectSubclasses(DifferentiateContext context, Utils utils, ReferenceID fromClass, boolean affectUsages) {
    debug("Affecting subclasses of class: " + fromClass + "; with usages affection: " + affectUsages);
    
    Graph graph = context.getGraph();
    utils.traverseSubclasses(fromClass, (Consumer<? super ReferenceID>)cl -> {
      for (NodeSource source : graph.getSources(cl)) {
        if (!utils.isCompiled(source)) {
          context.affectNodeSource(source);
        }
        if (affectUsages) {
          for (JvmClass node : Iterators.filter(graph.getNodes(source, JvmClass.class), n -> cl.equals(n.getReferenceID()))) {
            context.affectUsage(new ClassUsage(node.getName()));
            break;
          }
        }
      }
    });
  }

  private void debug(String message) {
    // todo
  }

}
