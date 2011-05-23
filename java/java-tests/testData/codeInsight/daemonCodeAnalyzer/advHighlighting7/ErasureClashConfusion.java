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
import java.util.HashMap;
import java.util.Map;

// IDEA-69629
class C {
  public static interface GenericAgnosticProcessor {
    void <warning descr="Method 'processMap(java.util.Map)' is never used">processMap</warning>(Map map);
    //   ^^^ to cdr: should not be marked as unused
  }

  public static interface GenericAwareProcessor {
    void <warning descr="Method 'processMap(java.util.Map<java.lang.String,java.lang.String>)' is never used">processMap</warning>(Map<String, String> map);
    //   ^^^ to cdr: should not be marked as unused
  }

  public static class TestProcessor implements GenericAwareProcessor, GenericAgnosticProcessor {
    @Override public void processMap(Map map) { }
  }

  public static void main(String[] args) {
    final TestProcessor testProcessor = new TestProcessor();
    testProcessor.processMap(new HashMap());
  }
}
