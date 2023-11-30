/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.siyeh.igtest.assignment.method_parameter;

class IgnoreTransformationOfParameter {

    public void incrementParameter(int value) {
        value++; // not flagged by the inspection
    }

    public void compoundAssignParameter(int value) {
        value += 1;  // flagged by the inspection
    }

    public void compoundAssignParameter(int value, int increment) {
        value += increment; // flagged by the inspection
    }

    public void foo(String s) {
        System.out.println(s);
        <warning descr="Assignment to method parameter 's'">s</warning> = "other";
        System.out.println(s);
    }

    public void method(int decreased, int increased) {
        decreased += 10; // not highlighted
        increased -= 10; // highlighted
    }

}