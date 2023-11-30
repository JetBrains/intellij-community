package com.siyeh.igtest.imports;

<warning descr="Package import 'import java.util.*;'">import java.util.*;</warning>
import java.io.File;

public class OnDemandImport
{
    public HashMap m_map;
    public File file;
}
