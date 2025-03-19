/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.siyeh.igtest.bugs.object_to_string;

import java.io.*;

class ObjectToString_IGNORE_TOSTRING
{
    static class N {}

    static class NN extends N {
        public String toString() {
            // calling super.toString() inside toString() implementation is considered ok if IGNORE_TOSTRING is set
            return super.toString() + ": NN";
        }

        public String toString(int x) {
            return <warning descr="Call to default 'toString()' on 'super'">super</warning>.toString() + ": NN";
        }
    }
}