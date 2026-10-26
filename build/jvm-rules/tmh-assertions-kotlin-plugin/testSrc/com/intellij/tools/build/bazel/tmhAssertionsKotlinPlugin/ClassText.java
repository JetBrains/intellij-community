// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.tmhAssertionsKotlinPlugin;

import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.Label;
import org.jetbrains.org.objectweb.asm.Opcodes;
import org.jetbrains.org.objectweb.asm.tree.AbstractInsnNode;
import org.jetbrains.org.objectweb.asm.tree.ClassNode;
import org.jetbrains.org.objectweb.asm.tree.LabelNode;
import org.jetbrains.org.objectweb.asm.tree.MethodNode;
import org.jetbrains.org.objectweb.asm.util.Textifier;
import org.jetbrains.org.objectweb.asm.util.TraceClassVisitor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A text form of a class file for a comparison. The constant pool order is not part of it. The text shows a label as
 * the index of the next instruction, without a line of its own. So two methods with the same instructions, line numbers
 * and local variables get the same text, also when one of them has an extra label. Stack map frames are not part of
 * it. The compiler merges two reference types to java.lang.Object. The class writer of the reference instrumentation
 * merges them to their common supertype.
 */
final class ClassText {
  private ClassText() {
  }

  static String of(byte[] classFile) {
    ClassNode node = new ClassNode();
    new ClassReader(classFile).accept(node, ClassReader.SKIP_FRAMES);
    Map<String, Map<Label, String>> labelsByMethod = new HashMap<>();
    for (MethodNode method : node.methods) {
      labelsByMethod.put(method.name + method.desc, labelNames(method));
    }
    StringWriter text = new StringWriter();
    node.accept(new TraceClassVisitor(null, new IndexLabelTextifier(labelsByMethod), new PrintWriter(text)));
    return text.toString();
  }

  /**
   * Names each label after the index of the next instruction. The name of a label after the last instruction is END.
   */
  private static Map<Label, String> labelNames(MethodNode method) {
    Map<Label, String> names = new HashMap<>();
    List<Label> pending = new ArrayList<>();
    int index = 0;
    for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
      if (insn instanceof LabelNode label) {
        pending.add(label.getLabel());
      }
      else if (insn.getOpcode() >= 0) {
        for (Label label : pending) {
          names.put(label, "I" + index);
        }
        pending.clear();
        index++;
      }
    }
    for (Label label : pending) {
      names.put(label, "END");
    }
    return names;
  }

  private static final class IndexLabelTextifier extends Textifier {
    private final Map<String, Map<Label, String>> myLabelsByMethod;
    private final Map<Label, String> myLabels;
    private String myCurrentMethod;

    IndexLabelTextifier(Map<String, Map<Label, String>> labelsByMethod) {
      this(labelsByMethod, Map.of());
    }

    private IndexLabelTextifier(Map<String, Map<Label, String>> labelsByMethod, Map<Label, String> labels) {
      super(Opcodes.API_VERSION);
      myLabelsByMethod = labelsByMethod;
      myLabels = labels;
    }

    @Override
    public Textifier visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
      myCurrentMethod = name + descriptor;
      return super.visitMethod(access, name, descriptor, signature, exceptions);
    }

    @Override
    protected Textifier createTextifier() {
      Map<Label, String> labels = myCurrentMethod != null ? myLabelsByMethod.getOrDefault(myCurrentMethod, myLabels) : myLabels;
      return new IndexLabelTextifier(myLabelsByMethod, labels);
    }

    @Override
    protected void appendLabel(Label label) {
      stringBuilder.append(myLabels.getOrDefault(label, "?"));
    }

    @Override
    public void visitLabel(Label label) {
    }
  }
}
