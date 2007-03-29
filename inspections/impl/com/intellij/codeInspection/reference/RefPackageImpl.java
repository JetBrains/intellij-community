/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Nov 15, 2001
 * Time: 5:17:38 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.reference;

import com.intellij.openapi.application.ApplicationManager;


public class RefPackageImpl extends RefEntityImpl implements RefPackage {
  private final String myQualifiedName;

  public RefPackageImpl(String name) {
    super(getPackageSuffix(name));
    myQualifiedName = name;
  }

  public String getQualifiedName() {
    return myQualifiedName;
  }

  private static String getPackageSuffix(String fullName) {
    int dotIndex = fullName.lastIndexOf('.');
    return (dotIndex >= 0) ? fullName.substring(dotIndex + 1) : fullName;
  }


  public void accept(final RefVisitor refVisitor) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        refVisitor.visitPackage(RefPackageImpl.this);
      }
    });
  }

  public String getExternalName() {
    return getQualifiedName();
  }

  public static RefEntity packageFromFQName(final RefManager manager, final String name) {
    return manager.getPackage(name);
  }

  public boolean isValid() {
    return true;
  }
}
