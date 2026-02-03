/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
class C {
    double sideEffect(int x) {
        System.out.println("Side effect"+x);
        return Math.random();
    }

    void m() {
        if (sideEffect(1) > 0.5 && sideEffect(2) < 0.1) {
            if (sideEffect(3) / sideEffect(4) < 1) {
                sideEffect(5);
            } else {
                sideEffect(6);
            }
        }
        if(false) {
            System.out.println("oops");
        }
    }
}
