/*
 * Copyright 2001-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.java.generate.exception;

/**
 * Error generating the javacode for the <code>toString</code> method.
 * <p/>
 * This exception is usually caused by a Velocity parsing exception.
 */
public class GenerateCodeException extends PluginException {

    /**
     * Error generating the java code.
     *
     * @param cause the caused exception.
     */
    public GenerateCodeException(Throwable cause) {
        super(cause);
    }

    /**
     * Error generating the java code.
     *
     * @param msg    message description.
     * @param cause  the caused exception.
     */
    public GenerateCodeException(String msg, Throwable cause) {
        super(msg, cause);
    }

}