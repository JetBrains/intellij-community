package com.intellij.tools.build.bazel.jvmIncBuilder.impl.forms;

import com.intellij.tools.build.bazel.jvmIncBuilder.BuildContext;
import com.intellij.tools.build.bazel.uiDesigner.compiler.AlienFormFileException;
import com.intellij.tools.build.bazel.uiDesigner.compiler.Utils;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.NodeSource;
import org.jetbrains.jps.dependency.NodeSourcePathMapper;
import org.jetbrains.jps.util.Iterators;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public interface FormBinding {
  String FORM_EXTENSION = ".form";

  NodeSource getBoundForm(String aClass);

  @Nullable
  String getBoundClass(NodeSource form);

  Iterable<NodeSource> getAllForms();

  static FormBinding create(BuildContext context) throws Exception {
    NodeSourcePathMapper pathMapper = context.getPathMapper();
    Map<NodeSource, String> formToClassMap = new HashMap<>();
    Map<String, NodeSource> classToFormMap = new HashMap<>();
    Map<String, Path> names = new HashMap<>();
    for (NodeSource form : Iterators.filter(context.getSources().getElements(), FormBinding::isForm)) {
      try {

        Path formPath = pathMapper.toPath(form);
        Path previous = names.put(formPath.getFileName().toString(), formPath);
        if (previous != null) {
          throw new Exception(
            "Equally named forms are not supported within the same build target. Following forms have the same file name:\n\t" + formPath + "\n\t" + previous
          );
        }

        String boundClass = Utils.getBoundClassName(formPath);
        if (boundClass != null) {
          formToClassMap.put(form, boundClass);
          NodeSource previousBoundForm = classToFormMap.put(boundClass, form);
          if (previousBoundForm != null) {
            throw new Exception(
              "The form " + form + " is bound to the class \"" + boundClass + "\".\\nAnother form " + previousBoundForm + " is also bound to this class"
            );
          }
        }
        else {
          throw new Exception(
            "Class to bind does not exist for the form " + form
          );
        }
      }
      catch (AlienFormFileException ignored) {
      }
    }
    return new FormBinding() {
      @Override
      public NodeSource getBoundForm(String aClass) {
        return classToFormMap.get(aClass);
      }

      @Override
      public @Nullable String getBoundClass(NodeSource form) {
        return formToClassMap.get(form);
      }

      @Override
      public Iterable<NodeSource> getAllForms() {
        return formToClassMap.keySet();
      }
    };
  }

  static boolean isForm(NodeSource src) {
    return src.toString().endsWith(FORM_EXTENSION);
  }
}
