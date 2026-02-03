// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.reference;

public interface WritableRefElement extends RefElement, WritableRefEntity {
  void addInReference(RefElement refElement);

  void addOutReference(RefElement refElement);

  void addSuppression(String text);
}
