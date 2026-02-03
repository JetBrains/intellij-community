package com.siyeh.igtest.security;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

public class JDBCPrepareStatementWithNonConstantString
{
    public JDBCPrepareStatementWithNonConstantString()
    {
    }

    public void foo() throws IOException, SQLException
    {
        Connection connection = null;
        connection.prepareStatement("foo");
        connection.<warning descr="Call to 'Connection.prepareStatement()' with non-constant argument">prepareStatement</warning>("foo" + bar());
        connection.<warning descr="Call to 'Connection.prepareCall()' with non-constant argument">prepareCall</warning>("foo" + bar());
    }

    private String bar() {
        return "bar";
    }
}