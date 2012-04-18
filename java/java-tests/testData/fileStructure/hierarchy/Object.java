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
public class Object {
  private static native void registerNatives();

  static {registerNatives();}

  public final native Class<?> getClass();

  public native int hashCode();

  public boolean equals(Object obj) {return false;}

  protected native Object clone() throws CloneNotSupportedException;

  public String toString() {return null;}

  public final native void notify();

  public final native void notifyAll();

  public final native void wait(long timeout) throws InterruptedException;

  public final void wait(long timeout, int nanos) throws InterruptedException {}

  public final void wait() throws InterruptedException {}

  protected void finalize() throws Throwable { }
}