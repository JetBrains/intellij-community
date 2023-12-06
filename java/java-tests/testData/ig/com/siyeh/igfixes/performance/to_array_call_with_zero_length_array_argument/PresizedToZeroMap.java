package com.siyeh.igfixes.performance.to_array_call_with_zero_length_array_argument;

import java.util.Map;

class IntroduceVariable {
  String[] keys(Map<String, String> map) {
    return map.keySet().toAr<caret>ray(new String[map.size()]);
  }
}