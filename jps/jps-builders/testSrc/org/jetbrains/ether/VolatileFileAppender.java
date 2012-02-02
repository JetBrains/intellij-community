/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.ether;

import org.apache.log4j.FileAppender;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

/**
 * Created with IntelliJ IDEA.
 * User: db
 * Date: 02.02.12
 * Time: 18:27
 * To change this template use File | Settings | File Templates.
 */

public class VolatileFileAppender extends FileAppender {
  private OutputStream myStream = null;
  
  public VolatileFileAppender() {
    super();
    IncrementalTestCase.setAppender(this);
  }

  void closeStream () throws IOException {
    if (myStream != null){
      myStream.close();
      myStream = null;
    }
  }
  
  @Override
  protected OutputStreamWriter createWriter(final OutputStream os) {
    myStream = os;
    return super.createWriter(os);
  }
}
