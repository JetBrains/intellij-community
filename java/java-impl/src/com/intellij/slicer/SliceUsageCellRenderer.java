/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.slicer;

import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usages.TextChunk;
import com.intellij.util.FontUtil;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

/**
 * @author cdr
 */
public class SliceUsageCellRenderer extends SliceUsageCellRendererBase {

  @Override
  public void customizeCellRendererFor(@NotNull SliceUsage sliceUsage) {
    boolean isForcedLeaf = sliceUsage instanceof JavaSliceDereferenceUsage;
    //might come SliceTooComplexDFAUsage
    JavaSliceUsage javaSliceUsage = sliceUsage instanceof JavaSliceUsage ? (JavaSliceUsage)sliceUsage : null;

    TextChunk[] text = sliceUsage.getText();
    final List<TextRange> usageRanges = new SmartList<TextRange>();
    sliceUsage.processRangeMarkers(new Processor<Segment>() {
      @Override
      public boolean process(Segment segment) {
        usageRanges.add(TextRange.create(segment));
        return true;
      }
    });
    boolean isInsideContainer = javaSliceUsage != null && javaSliceUsage.indexNesting != 0;
    for (int i = 0, length = text.length; i < length; i++) {
      TextChunk textChunk = text[i];
      SimpleTextAttributes attributes = textChunk.getSimpleAttributesIgnoreBackground();
      if (isForcedLeaf) {
        attributes = attributes.derive(attributes.getStyle(), JBColor.LIGHT_GRAY, attributes.getBgColor(), attributes.getWaveColor());
      }
      boolean inUsage = (attributes.getFontStyle() & Font.BOLD) != 0;
      if (isInsideContainer && inUsage) {
        //Color darker = Color.BLACK;//attributes.getBgColor() == null ? Color.BLACK : attributes.getBgColor().darker();
        //attributes = attributes.derive(SimpleTextAttributes.STYLE_OPAQUE, attributes.getFgColor(), UIUtil.getTreeBackground().brighter(), attributes.getWaveColor());
        //setMyBorder(IdeBorderFactory.createRoundedBorder(10, 3));
        //setPaintFocusBorder(true);
      }
      append(textChunk.getText(), attributes);
      if (i == 0) {
        append(FontUtil.spaceAndThinSpace());
      }
    }

    if (javaSliceUsage != null) {
      for (int i = 0; i < javaSliceUsage.indexNesting; i++) {
        append(
          " (Tracking container contents" +
          (javaSliceUsage.syntheticField.isEmpty() ? "" : " '" + javaSliceUsage.syntheticField + "'") +
          ")",
          SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      }
    }

    PsiElement element = sliceUsage.getElement();
    PsiMethod method;
    PsiClass aClass;
    while (true) {
      method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
      aClass = method == null ? PsiTreeUtil.getParentOfType(element, PsiClass.class) : method.getContainingClass();
      if (aClass instanceof PsiAnonymousClass) {
        element = aClass;
      }
      else {
        break;
      }
    }
    int methodOptions = PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS | PsiFormatUtilBase.SHOW_CONTAINING_CLASS;
    String location = method != null
                      ? PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, methodOptions, PsiFormatUtilBase.SHOW_TYPE, 2)
                      : aClass != null ? PsiFormatUtil.formatClass(aClass, PsiFormatUtilBase.SHOW_NAME) : null;
    if (location != null) {
      SimpleTextAttributes attributes = SimpleTextAttributes.GRAY_ATTRIBUTES;
      append(" in " + location, attributes);
    }
  }
}

