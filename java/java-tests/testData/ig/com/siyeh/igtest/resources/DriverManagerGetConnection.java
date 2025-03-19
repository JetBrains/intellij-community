package com.siyeh.igtest.resources;

import java.sql.DriverManager;
import java.sql.SQLException;

public class DriverManagerGetConnection {
    public void foo() throws SQLException {
        DriverManager.getConnection("foo");
    }
}
