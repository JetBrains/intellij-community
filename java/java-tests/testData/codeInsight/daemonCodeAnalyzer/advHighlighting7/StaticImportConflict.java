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
import x.Base1;
import static x.Base2.F;
import static x.Base1.m;
import static x.Base2.m;
import static <error descr="Field 'IF' is ambiguous in a single static import">x.Base2.II.IF</error>;

class Usage {
  void use() {
    m(Base1.F); //Base1.m(int)
    m(F); //Base2.m(float), float Base2.F
    F.class.getName(); // class Base2.F
    m(<error descr="Reference to 'IF' is ambiguous, both 'I1.IF' and 'I2.IF' match">IF</error>);
  }
}