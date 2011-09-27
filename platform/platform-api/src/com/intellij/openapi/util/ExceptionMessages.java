/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.util;

import com.intellij.CommonBundle;

import java.io.IOException;
import java.util.HashMap;

public class ExceptionMessages {
  static  final HashMap<Integer, String> ourIOMessages;
  static {
    ourIOMessages = new HashMap<Integer,String>();
    if(SystemInfo.isWindows) {
      ourIOMessages.put(new Integer(1), CommonBundle.message("incorrect.function.error.message"));
      ourIOMessages.put(new Integer(2), CommonBundle.message("the.system.cannot.find.the.file.specified.error.message"));
      ourIOMessages.put(new Integer(3), CommonBundle.message("the.system.cannot.find.the.path.specified.error.message"));
      ourIOMessages.put(new Integer(4), CommonBundle.message("the.system.cannot.open.the.file.error.message"));
      ourIOMessages.put(new Integer(5), CommonBundle.message("access.is.denied.error.message"));
      ourIOMessages.put(new Integer(6), CommonBundle.message("the.handle.is.invalid.error.message"));
      ourIOMessages.put(new Integer(7), CommonBundle.message("the.storage.control.blocks.were.destroyed.error.message"));
      ourIOMessages.put(new Integer(8), CommonBundle.message("not.enough.storage.is.available.to.process.this.command.error.message"));
      ourIOMessages.put(new Integer(9), CommonBundle.message("the.storage.control.block.address.is.invalid.error.message"));
      ourIOMessages.put(new Integer(10), CommonBundle.message("the.environment.is.incorrect.error.message"));
      ourIOMessages.put(new Integer(11),
                        CommonBundle.message("an.attempt.was.made.to.load.a.program.with.an.incorrect.format.error.message"));
      ourIOMessages.put(new Integer(12), CommonBundle.message("the.access.code.is.invalid.error.message"));
      ourIOMessages.put(new Integer(13), CommonBundle.message("the.data.is.invalid.error.message"));
      ourIOMessages.put(new Integer(14), CommonBundle.message("not.enough.storage.is.available.to.complete.this.operation.error.message"));
      ourIOMessages.put(new Integer(15), CommonBundle.message("the.system.cannot.find.the.drive.specified.error.message"));
      ourIOMessages.put(new Integer(16), CommonBundle.message("the.directory.cannot.be.removed.error.message"));
      ourIOMessages.put(new Integer(17), CommonBundle.message("the.system.cannot.move.the.file.to.a.different.disk.drive.error.message"));
      ourIOMessages.put(new Integer(18), CommonBundle.message("there.are.no.more.files.error.message"));
      ourIOMessages.put(new Integer(19), CommonBundle.message("the.media.is.write.protected.error.message"));
      ourIOMessages.put(new Integer(20), CommonBundle.message("the.system.cannot.find.the.device.specified.error.message"));
      ourIOMessages.put(new Integer(21), CommonBundle.message("the.device.is.not.ready.error.message"));
      ourIOMessages.put(new Integer(22), CommonBundle.message("the.device.does.not.recognize.the.command.error.message"));
      ourIOMessages.put(new Integer(23), CommonBundle.message("data.error.cyclic.redundancy.check.error.message"));
      ourIOMessages.put(new Integer(24),
                        CommonBundle.message("the.program.issued.a.command.but.the.command.length.is.incorrect.error.message"));
      ourIOMessages.put(new Integer(25), CommonBundle.message("the.drive.cannot.locate.a.specific.area.or.track.on.the.disk.error.message"));
      ourIOMessages.put(new Integer(26), CommonBundle.message("the.specified.disk.or.diskette.cannot.be.accessed.error.message"));
      ourIOMessages.put(new Integer(27), CommonBundle.message("the.drive.cannot.find.the.sector.requested.error.message"));
    }
  }

  public static String getMessage(IOException exception) {
    String exceptionMessage = exception.getMessage();
    String detailedMessage = null;
    int idx = exceptionMessage.indexOf('=');
    if(idx != -1) {
      int endIdx = idx + 1;
      for(; endIdx < exceptionMessage.length(); endIdx ++) {
        if(!Character.isDigit(exceptionMessage.charAt(endIdx))) break;
      }
      try {
        int errorNumber = Integer.parseInt(exceptionMessage.substring(idx + 1, endIdx));
        detailedMessage = ourIOMessages.get(new Integer(errorNumber));
      }
      catch (NumberFormatException e) {
      }
    }
    StringBuffer buf = new StringBuffer();
    buf.append(exceptionMessage);
    if(detailedMessage != null) {
      buf.append("\n");
      buf.append(detailedMessage);
    }

    return buf.toString();
  }
}
