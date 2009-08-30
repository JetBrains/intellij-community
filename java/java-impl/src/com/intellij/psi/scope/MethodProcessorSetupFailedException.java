package com.intellij.psi.scope;

import org.jetbrains.annotations.NonNls;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 31.01.2003
 * Time: 20:17:26
 * To change this template use Options | File Templates.
 */
public class MethodProcessorSetupFailedException extends Exception{
  public MethodProcessorSetupFailedException(@NonNls String message){
    super(message);
  }
}
