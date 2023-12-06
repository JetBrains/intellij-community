package com.siyeh.igtest.verbose;

import java.util.Map;
import java.util.HashMap;

final class TShortCut{
    private static final Map<String, String> mapText;

    static{
        mapText = new HashMap<String, String>();//error!!!!

        TShortCut.mapText.put("ESCAPE", "-1");
        TShortCut.mapText.put("BACK_SPACE", "8");

        TShortCut.mapText.put("alt X", "49");
        TShortCut.mapText.put("alt Y", "30");
        TShortCut.mapText.put("alt Z", "277");

    }

    public static String getKeyText(String code){
        return mapText.get(code);
    }
}
