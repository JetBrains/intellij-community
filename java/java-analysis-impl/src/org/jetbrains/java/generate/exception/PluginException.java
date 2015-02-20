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
 * Base plugin exception.
 */
public class PluginException extends RuntimeException {

    private final String message;

    /**
     * Create exception.
     *
     * @param msg    message description.
     * @param cause  the caused exception.
     */
    public PluginException(String msg, Throwable cause) {
        super(cause);
        this.message = (msg != null ? msg + "\nCaused by: " + cause.getMessage() : cause.getMessage());
    }

    /**
     * Create exception.
     *
     * @param cause  the caused exception.
     */
    public PluginException(Throwable cause) {
        super(cause);
        this.message = cause.getMessage();
    }

    /**
     * Get's the caused message.
     * @return  the caused message.
     */
    public String getMessage() {
        return message;
    }

}
