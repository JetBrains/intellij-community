package com.siyeh.igtest.resources;

import org.hibernate.SessionFactory;
import org.hibernate.classic.Session;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.naming.NamingEnumeration;
import java.io.*;
import java.net.Socket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.ServerSocket;

public class HibernateResourceInspection {
    public void foo() {
        final SessionFactory factory = null;
        factory.openSession();
    }
    public void foo2()
    {
        final SessionFactory factory = null;
        final Session session = factory.openSession();
    }

    public void foo3()
    {
        final SessionFactory factory = null;
        final Session session = factory.openSession();
        session.close();
    }


    public void foo5() {
        SessionFactory factory = null;
        Session session = null;
        try {
            session = factory.openSession();
        } finally {
            session.close();
        }
    }
}
