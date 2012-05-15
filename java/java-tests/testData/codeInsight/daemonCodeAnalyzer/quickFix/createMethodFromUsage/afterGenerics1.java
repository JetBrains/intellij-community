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

// "Create Method 'bar'" "true"
public class Test {
    <T extends String> void foo (T t1, T t2) {
        bar (t1, t2);
    }

    private <T extends String> void bar(T t1, T t2) {
        <caret><selection>//To change body of created methods use File | Settings | File Templates.</selection>
    }
}