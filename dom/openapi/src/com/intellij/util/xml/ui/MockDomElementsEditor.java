/*
 * Copyright 2000-2006 JetBrains s.r.o.
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
package com.intellij.util.xml.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.StableElement;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

/**
 * @author peter
 */
public class MockDomElementsEditor {
  private final Map<EditedElementDescription<? extends DomElement>, DomElement> myDomElements = new HashMap<EditedElementDescription<? extends DomElement>, DomElement>();
  private final Module myModule;
  private CommittablePanel myContents;
  private DomFileEditor myFileEditor;

  public MockDomElementsEditor(final Module module) {
    myModule = module;
  }

  protected final <T extends DomElement> T addEditedElement(final Class<T> aClass, final EditedElementDescription<T> description) {
    final DomManager domManager = DomManager.getDomManager(myModule.getProject());
    final T t = domManager.createStableValue(new Factory<T>() {
      public T create() {
        T weblogicRdbmsBean = description.find();
        if (weblogicRdbmsBean == null) {
          return createMockElement(aClass, myModule);
        }
        return weblogicRdbmsBean;
      }
    });
    myDomElements.put(description, t);
    return t;
  }

  protected final DomFileEditor initFileEditor(final BasicDomElementComponent component, final VirtualFile virtualFile, final String name) {
    myContents = component;
    final Project project = component.getProject();
    myFileEditor = new DomFileEditor<BasicDomElementComponent>(project, virtualFile, name, component) {
      public JComponent getPreferredFocusedComponent() {
        return null;
      }

      public void reset() {
        for (final Map.Entry<EditedElementDescription<? extends DomElement>, DomElement> entry : myDomElements.entrySet()) {
          final DomElement newValue = entry.getKey().find();
          final DomElement oldValue = entry.getValue();
          if (newValue != null && !newValue.equals(oldValue) || newValue == null && !oldValue.getManager().isMockElement(oldValue)) {
            ((StableElement)oldValue).revalidate();
          }
        }
        super.reset();
      }

      public void commit() {
        super.commit();
        new WriteCommandAction(project) {
          protected void run(Result result) throws Throwable {
            for (final Map.Entry<EditedElementDescription<? extends DomElement>, DomElement> entry : myDomElements.entrySet()) {
              final EditedElementDescription description = entry.getKey();
              final DomElement editedElement = entry.getValue();
              if (description.find() == null && editedElement.getXmlTag() != null) {
                DomElement element = description.addElement();
                element.copyFrom(editedElement);
                description.initialize(element);
                removeWatchedElement(editedElement);
                ((StableElement)editedElement).invalidate();
              }
            }
          }
        }.execute();
      }
    };
    final DomManager domManager = DomManager.getDomManager(project);
    for (final DomElement element : myDomElements.values()) {
      if (domManager.isMockElement(element)) {
        myFileEditor.addWatchedElement(element);
      }
    }
    return myFileEditor;
  }

  public final DomFileEditor getFileEditor() {
    return myFileEditor;
  }

  private <T extends DomElement> T createMockElement(final Class<T> aClass, final Module module) {
    final Project project = module.getProject();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        myContents.reset();
      }
    });
    final DomManager domManager = DomManager.getDomManager(project);
    final T mockElement = domManager.createMockElement(aClass, module, true);
    if (myFileEditor != null) {
      myFileEditor.addWatchedElement(mockElement);
    }
    return mockElement;
  }
}
