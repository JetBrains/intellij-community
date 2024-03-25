let logs = [{"eventId": "finished", "data": {"session": {"reason": "com.intellij.platform.ml.impl.ModelNotAcquiredOutcome"}}}, {
  "eventId": "finished", "data": {
    "session": {"random_seed": -1598672019}, "structure": {
      "additional": {"TierGit": {"description": {"not_used": {}, "used": {"has_user": true, "n_commits": 3}}, "id": -401817549}}, "main": {
        "TierCompletionSession": {
          "description": {
            "not_used": {}, "used": {"language_id": "TEXT", "git_user_is_Glebanister": true, "completion_type": "SMART", "call_order": 1}
          }, "id": -105197293, "analysis": {"very_good_session": true}
        }
      }, "nested": [{
        "additional": {},
        "main": {"TierLookup": {"description": {"not_used": {}, "used": {}}, "id": 38162, "analysis": {"lookup_index": 1}}},
        "nested": [{
          "additional": {}, "prediction": 0.8091002299577453, "main": {
            "TierItem": {
              "description": {"not_used": {}, "used": {"length": 5, "decorations": 0}}, "id": -1220935314, "analysis": {}
            }
          }
        }, {
          "additional": {}, "prediction": 0.3358094493527286, "main": {
            "TierItem": {
              "description": {"not_used": {}, "used": {"length": 5, "decorations": 0}}, "id": -782084434, "analysis": {}
            }
          }
        }]
      }, {
        "additional": {},
        "main": {"TierLookup": {"description": {"not_used": {}, "used": {}}, "id": 38349, "analysis": {"lookup_index": 2}}},
        "nested": [{
          "additional": {}, "prediction": 0.6959769118328721, "main": {
            "TierItem": {
              "description": {"not_used": {}, "used": {"length": 8, "decorations": 1}}, "id": 1198136891, "analysis": {}
            }
          }
        }, {
          "additional": {}, "prediction": 0.45402567637488944, "main": {
            "TierItem": {
              "description": {"not_used": {}, "used": {"length": 7, "decorations": 1}}, "id": 122089051, "analysis": {}
            }
          }
        }, {
          "additional": {}, "main": {
            "TierItem": {
              "description": {"not_used": {}, "used": {"length": 17, "decorations": 1}}, "id": 1444769257, "analysis": {}
            }
          }
        }]
      }]
    }
  }
}, {"eventId": "finished", "data": {"session": {"throwable_class": "java.lang.IllegalStateException"}}}]