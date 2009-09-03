package com.intellij.codeInsight.lookup;

import com.intellij.openapi.project.Project;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Sep 29, 2004
 * Time: 7:38:32 PM
 * To change this template use File | Settings | File Templates.
 * @deprecated use InsertHandler
 */
public interface DeferredUserLookupValue<T> extends PresentableLookupValue {
  boolean handleUserSelection(LookupItem<T> item,Project project);
}
