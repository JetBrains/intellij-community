package org.driver.factory;

import org.declaration.API;
import org.declaration.impl.DummyApi;

public class ApiFactory {
    public static API create() {
        return new DummyApi();
    }
}
