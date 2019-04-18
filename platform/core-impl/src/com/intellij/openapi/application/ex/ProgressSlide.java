// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.ex;

import javax.swing.*;

public class ProgressSlide{
  private final String myUrl;
  private final float myProgressRation;
  private Icon myImage;

  public ProgressSlide(String url, float progressRatio) {
    myUrl = url;
    myProgressRation = progressRatio;
  }

  public float getProgressRation() {
    return myProgressRation;
  }

  public String getUrl() {
    return myUrl;
  }

  public void setImageIcon(Icon image) {
    myImage = image;
  }

  public Icon getLoadedImage() {
    if(myImage == null)
      throw new UnsupportedOperationException(String.format("Image for the slide %1$s is not set", myUrl));

    return myImage;
  }
}
