// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.formatting.fileSet.FileSetDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

public class ExcludedFiles {
  private final List<FileSetDescriptor> myDescriptors = ContainerUtil.newArrayList();

  @SuppressWarnings("unused") // Serialization
  public List<String> getDO_NOT_FORMAT() {
    return myDescriptors.stream()
      .map(descriptor -> descriptor.getSpec()).collect(Collectors.toList());
  }

  @SuppressWarnings("unused") // Serialization
  public void setDO_NOT_FORMAT(@NotNull List<String> specList) {
    myDescriptors.clear();
    for (String spec : specList) {
      myDescriptors.add(new FileSetDescriptor(spec));
    }
  }

  public void serializeInto(@NotNull Element element) {
    if (myDescriptors.size() > 0) {
      XmlSerializer.serializeInto(this, element);
    }
  }

  public void deserializeFrom(@NotNull Element element) {
    XmlSerializer.deserializeInto(this, element);
  }

  public void addDescriptor(@NotNull String fileSpec) {
    myDescriptors.add(new FileSetDescriptor(fileSpec));
  }

  public List<FileSetDescriptor> getDescriptors() {
    return myDescriptors;
  }

  public boolean contains(@NotNull PsiFile file) {
    Project project = file.getProject();
    for (FileSetDescriptor descriptor : myDescriptors) {
      if (descriptor.matches(project, file.getVirtualFile())) return true;
    }
    return false;
  }
}
