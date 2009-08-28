/*
 * User: anna
 * Date: 18-Dec-2007
 */
package com.intellij.codeInspection.reference;

public class RefVisitor {
  public void visitElement(RefEntity elem) {}

  public void visitFile(RefFile file) {
    visitElement(file);
  }

  public void visitModule(RefModule module){
    visitElement(module);
  }

  public void visitDirectory(RefDirectory directory) {
    visitElement(directory);
  }
}