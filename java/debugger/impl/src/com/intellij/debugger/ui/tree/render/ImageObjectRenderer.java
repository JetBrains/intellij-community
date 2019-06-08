// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.FullValueEvaluatorProvider;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.ClassLoadingUtils;
import com.intellij.debugger.impl.DebuggerUtilsImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.rt.debugger.ImageSerializer;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
import com.sun.jdi.ClassType;
import com.sun.jdi.Method;
import com.sun.jdi.Value;
import org.intellij.images.editor.impl.ImageEditorManagerImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;

class ImageObjectRenderer extends CompoundReferenceRenderer implements FullValueEvaluatorProvider {
  private static final Logger LOG = Logger.getInstance(ImageObjectRenderer.class);

  ImageObjectRenderer() {
    super("Image", null, null);
    setClassName("java.awt.Image");
    setEnabled(true);
  }

  @Nullable
  @Override
  public XFullValueEvaluator getFullValueEvaluator(final EvaluationContextImpl evaluationContext, final ValueDescriptorImpl valueDescriptor) {
    return new IconPopupEvaluator(DebuggerBundle.message("message.node.show.image"), evaluationContext) {
      @Override
      protected Icon getData() {
        return getIcon(getEvaluationContext(), valueDescriptor.getValue(), "imageToBytes");
      }
    };
  }

  static JComponent createIconViewer(@Nullable Icon icon) {
    if (icon == null) return new JLabel("No data", SwingConstants.CENTER);
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
      Value bytes = getImageBytes(evaluationContext, obj, methodName);
      byte[] data = DebuggerUtilsImpl.readBytesArray(bytes);
      if (data != null) {
        return new ImageIcon(data);
      }
    }
    catch (Exception e) {
      LOG.info("Exception while getting image data", e);
    }
    return null;
  }

  private static Value getImageBytes(EvaluationContext evaluationContext, Value obj, String methodName)
    throws EvaluateException {
    DebugProcess process = evaluationContext.getDebugProcess();
    EvaluationContext copyContext = evaluationContext.createEvaluationContext(obj);
    ClassType helperClass = ClassLoadingUtils.getHelperClass(ImageSerializer.class, copyContext);

    if (helperClass != null) {
      List<Method> methods = helperClass.methodsByName(methodName);
      if (!methods.isEmpty()) {
        return process.invokeMethod(copyContext, helperClass, methods.get(0), Collections.singletonList(obj));
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
