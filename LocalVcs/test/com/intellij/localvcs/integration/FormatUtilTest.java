package com.intellij.localvcs.integration;

import junit.framework.Assert;
import org.junit.Test;

import java.util.Date;

public class FormatUtilTest {
  @Test
  public void testFormatting() {
    Date d = new Date(2003, 01, 01, 12, 30);
    Assert.assertEquals("01.02.03 12:30", FormatUtil.formatTimestamp(d.getTime()));
  }
}
