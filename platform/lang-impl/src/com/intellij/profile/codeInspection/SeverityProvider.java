/*
 * User: anna
 * Date: 31-Jul-2007
 */
package com.intellij.profile.codeInspection;

import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;

public interface SeverityProvider {
  SeverityRegistrar getSeverityRegistrar();

  SeverityRegistrar getOwnSeverityRegistrar();
}