/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger;

import com.intellij.debugger.requests.ClassPrepareRequestor;
import com.sun.jdi.Location;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.request.ClassPrepareRequest;

import java.util.List;

public interface PositionManager {
  public SourcePosition      getSourcePosition (Location location) throws NoDataException;
  public List<ReferenceType> getAllClasses     (SourcePosition classPosition) throws NoDataException;
  public List<Location>      locationsOfLine   (ReferenceType type,
                                                  SourcePosition position) throws NoDataException;

  public ClassPrepareRequest createPrepareRequest(ClassPrepareRequestor requestor, SourcePosition position)throws NoDataException;
}
