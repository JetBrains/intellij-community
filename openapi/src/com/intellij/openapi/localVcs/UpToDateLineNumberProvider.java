package com.intellij.openapi.localVcs;

/**
 * Created by IntelliJ IDEA.
 * User: lesya
 * Date: Aug 13, 2004
 * Time: 9:43:01 PM
 * To change this template use File | Settings | File Templates.
 */
public interface UpToDateLineNumberProvider {
  int ABSENT_LINE_NUMBER = -1;

  int getLineNumber(int currentNumber);
}
