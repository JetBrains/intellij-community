// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.tree.render;

import com.intellij.CommonBundle;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.FullValueEvaluatorProvider;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.rt.debugger.ImageSerializer;
import com.sun.jdi.StringReference;
import com.sun.jdi.Value;
import org.intellij.images.editor.impl.ImageEditorManagerImpl;
import org.jetbrains.annotations.Nls;
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

  static @Nullable ImageIcon getIcon(EvaluationContextImpl evaluationContext, Value obj, String methodName) {
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

  private static byte @Nullable [] getImageBytes(EvaluationContextImpl evaluationContext, Value obj, String methodName)
    throws EvaluateException {
    EvaluationContextImpl copyContext = evaluationContext.createEvaluationContext(obj);
    StringReference bytes =
      (StringReference)DebuggerUtilsImpl.invokeHelperMethod(copyContext, ImageSerializer.class, methodName, Collections.singletonList(obj));
    if (bytes != null) {
      return bytes.value().getBytes(StandardCharsets.ISO_8859_1);
    }
    return null;
  }

  abstract static class IconPopupEvaluator extends CustomPopupFullValueEvaluator<Icon> {
    IconPopupEvaluator(@NotNull @Nls String linkText, @NotNull EvaluationContextImpl evaluationContext) {
      super(linkText, evaluationContext);
    }

    @Override
    protected JComponent createComponent(Icon data) {
      return createIconViewer(data);
    }
  }
}
