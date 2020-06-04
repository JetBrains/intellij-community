// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.tree.render;

import com.intellij.CommonBundle;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.FullValueEvaluatorProvider;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.ClassLoadingUtils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.rt.debugger.ImageSerializer;
import com.sun.jdi.ClassType;
import com.sun.jdi.Method;
import com.sun.jdi.StringReference;
import com.sun.jdi.Value;
import org.intellij.images.editor.impl.ImageEditorManagerImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

final class ImageObjectRenderer extends CompoundRendererProvider {
  private static final Logger LOG = Logger.getInstance(ImageObjectRenderer.class);

  @Override
  protected String getName() {
    return "Image";
  }

  @Override
  protected String getClassName() {
    return "java.awt.Image";
  }

  @Override
  protected boolean isEnabled() {
    return true;
  }

  @Override
  protected FullValueEvaluatorProvider getFullValueEvaluatorProvider() {
    return (evaluationContext, valueDescriptor) ->
      new IconPopupEvaluator(JavaDebuggerBundle.message("message.node.show.image"), evaluationContext) {
        @Override
        protected Icon getData() {
          return getIcon(getEvaluationContext(), valueDescriptor.getValue(), "imageToBytes");
        }
      };
  }

  static JComponent createIconViewer(@Nullable Icon icon) {
    if (icon == null) return new JLabel(CommonBundle.message("label.no.data"), SwingConstants.CENTER);
    final int w = icon.getIconWidth();
    final int h = icon.getIconHeight();
    final BufferedImage image = GraphicsEnvironment.getLocalGraphicsEnvironment()
      .getDefaultScreenDevice().getDefaultConfiguration().createCompatibleImage(w, h, Transparency.TRANSLUCENT);
    final Graphics2D g = image.createGraphics();
    icon.paintIcon(null, g, 0, 0);
    g.dispose();

    return ImageEditorManagerImpl.createImageEditorUI(image);
  }

  @Nullable
  static ImageIcon getIcon(EvaluationContext evaluationContext, Value obj, String methodName) {
    try {
      byte[] data = getImageBytes(evaluationContext, obj, methodName);
      if (data != null) {
        return new ImageIcon(data);
      }
    }
    catch (Exception e) {
      LOG.info("Exception while getting image data", e);
    }
    return null;
  }

  private static byte @Nullable [] getImageBytes(EvaluationContext evaluationContext, Value obj, String methodName)
    throws EvaluateException {
    DebugProcess process = evaluationContext.getDebugProcess();
    EvaluationContext copyContext = evaluationContext.createEvaluationContext(obj);
    ClassType helperClass = ClassLoadingUtils.getHelperClass(ImageSerializer.class, copyContext);

    if (helperClass != null) {
      Method method = DebuggerUtils.findMethod(helperClass, methodName, null);
      if (method != null) {
        StringReference bytes = (StringReference)process.invokeMethod(copyContext, helperClass, method, Collections.singletonList(obj));
        if (bytes != null) {
          return bytes.value().getBytes(StandardCharsets.ISO_8859_1);
        }
      }
    }
    return null;
  }

  static abstract class IconPopupEvaluator extends CustomPopupFullValueEvaluator<Icon> {
    IconPopupEvaluator(@NotNull String linkText, @NotNull EvaluationContextImpl evaluationContext) {
      super(linkText, evaluationContext);
    }

    @Override
    protected JComponent createComponent(Icon data) {
      return createIconViewer(data);
    }
  }
}
