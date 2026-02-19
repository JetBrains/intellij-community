package org.jetbrains.providers;

import org.jetbraons.api.MyProviderInterface;

public class WrongPlace implements MyProviderInterface {
    private static int a = 0;

    public static WrongPlace provider() {
        return new WrongPlace(<caret>);
    }

    public WrongPlace() {}
}
