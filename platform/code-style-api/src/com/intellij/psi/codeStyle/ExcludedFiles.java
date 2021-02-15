// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.application.options.CodeStyle;
import com.intellij.formatting.fileSet.FileSetDescriptor;
import com.intellij.formatting.fileSet.FileSetDescriptorFactory;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.OptionTag;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ExcludedFiles {
  private final List<FileSetDescriptor> myDescriptors = new ArrayList<>();
  private final State myState = new State();

  public void serializeInto(@NotNull Element element) {
    if (myDescriptors.size() > 0) {
      XmlSerializer.serializeInto(myState, element);
    }
  }

  public void deserializeFrom(@NotNull Element element) {
    XmlSerializer.deserializeInto(myState, element);
  }

  public void addDescriptor(@NotNull FileSetDescriptor descriptor) {
    myDescriptors.add(descriptor);
  }

  public List<FileSetDescriptor> getDescriptors() {
    return myDescriptors;
  }


  public void setDescriptors(@NotNull List<? extends FileSetDescriptor> descriptors) {
    myDescriptors.clear();
    myDescriptors.addAll(descriptors);
  }

  public boolean contains(@NotNull PsiFile file) {
    PsiFile settingsFile = CodeStyle.getSettingsPsi(file);
    if (settingsFile != null) {
      for (FileSetDescriptor descriptor : myDescriptors) {
        if (descriptor.matches(settingsFile)) return true;
      }
    }
    return false;
  }

  public void clear() {
    myDescriptors.clear();
  }

  public boolean equals(@NotNull Object o) {
    return o instanceof ExcludedFiles && myDescriptors.equals(((ExcludedFiles)o).myDescriptors);
  }

  public class State {
    @OptionTag("DO_NOT_FORMAT")
    public List<FileSetDescriptor.State> getDescriptors() {
      return ContainerUtil.map(myDescriptors, descriptor -> descriptor.getState());
    }

    public void setDescriptors(@NotNull List<FileSetDescriptor.State> states) {
      myDescriptors.clear();
      for (FileSetDescriptor.State state : states) {
        for (FileSetDescriptorFactory factory : FileSetDescriptorFactory.EP_NAME.getExtensionList()) {
          FileSetDescriptor descriptor = factory.createDescriptor(state);
          if (descriptor != null) {
            myDescriptors.add(descriptor);
          }
        }
      }
    }
  }
}
