package org.jetbrains.providers;

import org.jetbraons.api.MyProviderInterface;

public record MyRecord(String a) implements MyProviderInterface {
    public static MyRecord provider() {
        return new MyRecord(<caret>);
    }
}
