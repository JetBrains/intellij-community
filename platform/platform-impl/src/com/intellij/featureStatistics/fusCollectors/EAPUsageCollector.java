// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics.fusCollectors;

import com.intellij.idea.Main;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.LicensingFacade;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 */
public class EAPUsageCollector extends ApplicationUsagesCollector {
  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages() {
    return collectUsages();
  }

  @NotNull
  private static Set<UsageDescriptor> collectUsages() {
    try {
      if (!Main.isHeadless()) {
        final Set<UsageDescriptor> result = new HashSet<>();
        if (ApplicationInfoEx.getInstanceEx().isEAP()) {
          result.add(new UsageDescriptor("eap", 1));
        }
        else {
          result.add(new UsageDescriptor("release", 1));
        }
        final LicensingFacade facade = LicensingFacade.getInstance();
        if (facade != null) {
          // non-eap commercial version
          if (facade.isEvaluationLicense()) {
            result.add(new UsageDescriptor("evaluation", 1));
          }
          else if (!StringUtil.isEmpty(facade.getLicensedToMessage())){
            result.add(new UsageDescriptor("license", 1));
          }
        }
        return result;
      }
    }
    catch (Throwable e) {
    }
    return Collections.emptySet();
  }

  @NotNull
  @Override
  public String getGroupId() {
    return "statistics.user.advanced.info";
  }
}
