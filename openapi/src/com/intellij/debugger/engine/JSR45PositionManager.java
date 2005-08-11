/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.engine;

import com.intellij.debugger.NoDataException;
import com.intellij.debugger.PositionManager;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.requests.ClassPrepareRequestor;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.j2ee.deployment.JspDeploymentManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiFile;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Location;
import com.sun.jdi.ObjectCollectedException;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.request.ClassPrepareRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Apr 5, 2004
 * Time: 2:18:27 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class JSR45PositionManager implements PositionManager {
  private final DebugProcess      myDebugProcess;
  private final JspDeploymentManager myHelper;
  private final String            JSP_PATTERN;

  public JSR45PositionManager(DebugProcess debugProcess) {
    myDebugProcess = debugProcess;
    myHelper =  ApplicationManager.getApplication().getComponent(JspDeploymentManager.class);
    String jsp_pattern = getJSPClassesPackage();
    if(jsp_pattern.equals("")) {
      jsp_pattern = getJSPClassesNamePattern();
    }
    else {
      jsp_pattern = jsp_pattern + "." + getJSPClassesNamePattern();
    }

    JSP_PATTERN = jsp_pattern;
  }

  public SourcePosition getSourcePosition(final Location location) throws NoDataException {
    SourcePosition sourcePosition = null;

    try {
      String sourcePath = getRelativePath(location.sourcePath("JSP"));
      PsiFile file = myHelper.getDeployedJspSource(sourcePath, myDebugProcess.getProject());
      if(file == null) throw new NoDataException();
      //noinspection HardCodedStringLiteral
      int lineNumber = location.lineNumber("JSP");
      sourcePosition = SourcePosition.createFromLine(file, lineNumber - 1);
    }
    catch (AbsentInformationException e) {
    }

    if(sourcePosition == null) throw new NoDataException();

    return sourcePosition;
  }

  public List<ReferenceType> getAllClasses(SourcePosition classPosition) throws NoDataException {
    final FileType fileType = classPosition.getFile().getFileType();
    if(fileType != StdFileTypes.JSP && fileType != StdFileTypes.JSPX) {
      throw new NoDataException();
    }

    List<ReferenceType> referenceTypes = myDebugProcess.getVirtualMachineProxy().allClasses();

    List<ReferenceType> result = new ArrayList<ReferenceType>();

    final String regex = JSP_PATTERN.replaceAll("\\*", ".*");
    final Matcher matcher = Pattern.compile(regex).matcher("");

    for (Iterator<ReferenceType> iterator = referenceTypes.iterator(); iterator.hasNext();) {
      ReferenceType referenceType = iterator.next();
      matcher.reset(referenceType.name());
      if(!matcher.matches()) {
        continue;
      }

      List<Location> locations = locationsOfClassAt(referenceType, classPosition);
      if(locations != null) {
        result.add(referenceType);
      }
    }

    return result;
  }

  public List<Location> locationsOfLine(final ReferenceType type, final SourcePosition position) throws NoDataException {
    List<Location> locations = locationsOfClassAt(type, position);
    return locations != null ? locations : Collections.EMPTY_LIST;

  }

  private List<Location> locationsOfClassAt(final ReferenceType type, final SourcePosition position) throws NoDataException {
    final FileType fileType = position.getFile().getFileType();
    if(fileType != StdFileTypes.JSP && fileType != StdFileTypes.JSPX) {
      throw new NoDataException();
    }

    return ApplicationManager.getApplication().runReadAction(new Computable<List<Location>>() {
      public List<Location> compute() {
        try {
          PsiFile file = null;
          //noinspection HardCodedStringLiteral
          List<String> paths = (List<String>)type.sourcePaths("JSP");
          for (Iterator<String> iterator = paths.iterator(); iterator.hasNext();) {
            String path = iterator.next();
            file = myHelper.getDeployedJspSource(getRelativePath(path), myDebugProcess.getProject());
            if(file != null) break;
          }

          if(file != null && file.equals(position.getFile())) {
            //noinspection HardCodedStringLiteral
            return (List<Location>)type.locationsOfLine("JSP", type.sourceName(), position.getLine() + 1);
          }
        }
        catch (ObjectCollectedException e) {
        }
        catch (AbsentInformationException e) {
        }
        catch (InternalError e) {
          myDebugProcess.getExecutionResult().getProcessHandler().notifyTextAvailable("Internal error when loading debug information from '" + type.name() + "'.  Breakpoints will be unavailable in this class.", ProcessOutputTypes.SYSTEM);
        }
        return null;
      }
    });
  }

  public ClassPrepareRequest createPrepareRequest(final ClassPrepareRequestor requestor, final SourcePosition position)
    throws NoDataException {
    final FileType fileType = position.getFile().getFileType();
    if(fileType != StdFileTypes.JSP && fileType != StdFileTypes.JSPX) {
      throw new NoDataException();
    }

    return myDebugProcess.getRequestsManager().createClassPrepareRequest(new ClassPrepareRequestor() {
      public void processClassPrepare(DebugProcess debuggerProcess, ReferenceType referenceType) {
        try {
          if(locationsOfClassAt(referenceType, position) != null) {
            requestor.processClassPrepare(debuggerProcess, referenceType);
          }
        }
        catch (NoDataException e) {
        }
      }
    }, JSP_PATTERN);
  }

  protected String getRelativePath(String jspPath) {
    return jspPath;
  }

  protected abstract String getJSPClassesPackage();

  protected String getJSPClassesNamePattern() {
    return "*";
  }
}
