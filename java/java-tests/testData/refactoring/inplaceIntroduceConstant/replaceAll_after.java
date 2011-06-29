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
class Test {
    private static final Object LOG = null;
    public static final String THROWS = "throws";
    private final Set<Map> myMethods = new HashSet<Map>();
    private final Set<Map> myMethods1 = new HashSet<Map>();
    public void method(Map<Object, Object> args) {
        final Object toThrow = args.get(THROWS);
        if (toThrow instanceof List) {
            final ArrayList<String> list = new ArrayList<String>();
            args.put(THROWS, list);
        } else if (toThrow != null) {
            args.put(THROWS, Collections.singleton(toThrow));
        }
        myMethods.add(args);
    }
}
