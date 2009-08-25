package com.intellij.openapi.roots;

import org.jetbrains.annotations.NonNls;
import com.intellij.util.ArrayUtil;

/**
 * @author yole
 */
public class PersistentOrderRootType extends OrderRootType {
  private final String mySdkRootName;
  private final String myModulePathsName;
  private final String myOldSdkRootName;

  protected PersistentOrderRootType(@NonNls String name, @NonNls String sdkRootName, @NonNls String modulePathsName, @NonNls final String oldSdkRootName) {
    super(name);
    mySdkRootName = sdkRootName;
    myModulePathsName = modulePathsName;
    myOldSdkRootName = oldSdkRootName;
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    ourPersistentOrderRootTypes = ArrayUtil.append(ourPersistentOrderRootTypes, this);
  }

  /**
   * Element name used for storing roots of this type in JDK and library definitions.
   */
  public String getSdkRootName() {
    return mySdkRootName;
  }

  public String getOldSdkRootName() {
    return myOldSdkRootName;
  }

  /**
   * Element name used for storing roots of this type in module definitions.
   */
  public String getModulePathsName() {
    return myModulePathsName;
  }

}
