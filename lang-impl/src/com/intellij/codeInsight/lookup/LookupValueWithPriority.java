package com.intellij.codeInsight.lookup;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Jun 20, 2005
 * Time: 8:12:52 PM
 * To change this template use File | Settings | File Templates.
 */
public interface LookupValueWithPriority {
  int NORMAL = 0;
  int HIGHER = 1;
  int HIGH = 2;
  
  int getPriority();
}
