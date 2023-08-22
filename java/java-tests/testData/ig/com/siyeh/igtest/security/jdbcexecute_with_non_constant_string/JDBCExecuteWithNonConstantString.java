package com.siyeh.igtest.security;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class JDBCExecuteWithNonConstantString
{
    private static final String CREATE_TABLE_FILTERS;
    static {
        CREATE_TABLE_FILTERS = "CREATE TABLE filters ( id INT NOT NULL AUTO_INCREMENT," + " field VARCHAR(128) NOT NULL," + " value varchar(256) NOT NULL," + " CONSTRAINT filter_pkey PRIMARY KEY (field, value) )";
    }

    public JDBCExecuteWithNonConstantString()
    {
    }

    public void foo() throws IOException, SQLException
    {
        Statement statement = null;
        statement.executeQuery("foo" );
        statement.<warning descr="Call to 'Statement.executeQuery()' with non-constant argument">executeQuery</warning>("foo" + bar());
        statement.<warning descr="Call to 'Statement.addBatch()' with non-constant argument">addBatch</warning>("foo" + bar());
        statement.execute(CREATE_TABLE_FILTERS);
    }

    private String bar() {
        return "bar";
    }
}