/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.usages;

import com.intellij.usageView.UsageInfo;
import com.intellij.util.Icons;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 17, 2005
 */
public class ReadWriteAccessUsageInfo2UsageAdapter extends UsageInfo2UsageAdapter implements ReadWriteAccessUsage{
  private final boolean myAccessedForReading;
  private final boolean myAccessedForWriting;

  public ReadWriteAccessUsageInfo2UsageAdapter(final UsageInfo usageInfo, final boolean accessedForReading, final boolean accessedForWriting) {
    super(usageInfo);
    myAccessedForReading = accessedForReading;
    myAccessedForWriting = accessedForWriting;
    if (myIcon == null) {
      if (myAccessedForReading && myAccessedForWriting) {
        myIcon = Icons.VARIABLE_RW_ACCESS;
      }
      else if (myAccessedForWriting) {
        myIcon = Icons.VARIABLE_WRITE_ACCESS;           // If icon is changed, don't forget to change UTCompositeUsageNode.getIcon();
      }
      else if (myAccessedForReading){
        myIcon = Icons.VARIABLE_READ_ACCESS;            // If icon is changed, don't forget to change UTCompositeUsageNode.getIcon();
      }
    }
  }

  public boolean isAccessedForWriting() {
    return myAccessedForWriting;
  }

  public boolean isAccessedForReading() {
    return myAccessedForReading;
  }



}
