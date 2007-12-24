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
import com.intellij.util.Icons;

import javax.swing.*;


public class RefPackageImpl extends RefEntityImpl implements RefPackage {
  private final String myQualifiedName;

  public RefPackageImpl(String name, RefManager refManager) {
    super(getPackageSuffix(name), refManager);
    myQualifiedName = name;
  }

  public String getQualifiedName() {
    return myQualifiedName;
  }

  private static String getPackageSuffix(String fullName) {
    int dotIndex = fullName.lastIndexOf('.');
    return (dotIndex >= 0) ? fullName.substring(dotIndex + 1) : fullName;
  }


  public void accept(final RefVisitor visitor) {
    if (visitor instanceof RefJavaVisitor) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          ((RefJavaVisitor)visitor).visitPackage(RefPackageImpl.this);
        }
      });
    } else {
      super.accept(visitor);
    }
  }

  public String getExternalName() {
    return getQualifiedName();
  }

  public static RefEntity packageFromFQName(final RefManager manager, final String name) {
    return manager.getExtension(RefJavaManager.MANAGER).getPackage(name);
  }

  public boolean isValid() {
    return true;
  }

  public Icon getIcon(final boolean expanded) {
    return expanded ? Icons.PACKAGE_OPEN_ICON : Icons.PACKAGE_ICON;
  }
}
