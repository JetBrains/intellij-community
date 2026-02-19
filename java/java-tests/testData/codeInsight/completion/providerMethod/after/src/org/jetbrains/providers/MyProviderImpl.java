package org.jetbrains.providers;

import org.jetbraons.api.MyProviderInterface;

public class MyProviderImpl implements MyProviderInterface {
    public static MyProviderImpl provider() {
        return new MyProviderImpl(<caret>);
    }
}
