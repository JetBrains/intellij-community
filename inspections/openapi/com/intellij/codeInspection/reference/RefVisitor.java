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
package com.intellij.codeInspection.reference;

/**
 * Visitor for reference graph nodes.
 *
 * @see RefEntity#accept
 * @see RefManager#iterate
 * @since 6.0
 */
public class RefVisitor {
  public void visitElement(RefEntity elem) {

  }

  public void visitField(RefField field) {
    visitElement(field);
  }

  public void visitMethod(RefMethod method) {
    visitElement(method);
  }

  public void visitParameter(RefParameter parameter) {
    visitElement(parameter);
  }

  public void visitClass(RefClass aClass) {
    visitElement(aClass);
  }

  public void visitFile(RefFile file) {
    visitElement(file);
  }

  public void visitModule(RefModule module){
    visitElement(module);
  }

  public void visitPackage(RefPackage aPackage) {
    visitElement(aPackage);
  }
}
