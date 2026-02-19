package com.siyeh.igtest.imports;

import java.util.*;
<warning descr="Single class import 'import java.io.File;'">import java.io.File;</warning>

public class SingleClassImport
{
    public HashMap m_map;
    public File file;
}
