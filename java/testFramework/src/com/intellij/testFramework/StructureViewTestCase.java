/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.testFramework;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;


public abstract class StructureViewTestCase extends CodeInsightTestCase {
  protected interface Test {
    void test(StructureViewComponent component);
  }

  protected void doTest(final Test test) {
    assert myFile != null : "configure first";

    final VirtualFile vFile = myFile.getVirtualFile();
    assert vFile != null : "no virtual file for " + myFile;

    final FileEditor fileEditor = FileEditorManager.getInstance(getProject()).getSelectedEditor(vFile);
    assert fileEditor != null : "editor not opened for " + vFile;

    final StructureViewBuilder builder = LanguageStructureViewBuilder.INSTANCE.getStructureViewBuilder(myFile);
    assert builder != null : "no builder for " + myFile;

    StructureViewComponent component = null;
    try {
      component = (StructureViewComponent)builder.createStructureView(fileEditor, myProject);
      test.test(component);
    }
    finally {
      if (component != null) Disposer.dispose(component);
    }
  }
}
