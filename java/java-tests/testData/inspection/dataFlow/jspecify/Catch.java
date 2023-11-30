/*
 * Copyright 2020 The JSpecify Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import org.jspecify.annotations.NullMarked;

@NullMarked
abstract class Catch {
  void x() {
    try {
      throw new Exception();
    } catch (Exception e) {
      e.printStackTrace();
      // TODO(cpovirk): Edit README to permit referencing java.lang.Exception. Or remove this.
    }

    try {
      doWork();
    } catch (SomeError | SomeRuntimeException e) {
    } catch (Throwable e) {
      e.printStackTrace();
    }

    try {
      throw new RuntimeException();
    } catch (RuntimeException | Error e) {
      handleException(e);
    }
  }

  abstract void handleException(Throwable t);

  abstract void doWork() throws SomeException;

  static class SomeException extends Exception {}

  static class SomeRuntimeException extends RuntimeException {}

  static class SomeError extends Error {}
}
