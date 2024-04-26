package org.jetbrains.providers;

import org.jetbraons.api.MyProviderInterface;

public class WrongPlace implements MyProviderInterface {
    public static void method() {
        pr<caret>
    }

    private static void program() {

    }

    private static void process() {

    }
}
