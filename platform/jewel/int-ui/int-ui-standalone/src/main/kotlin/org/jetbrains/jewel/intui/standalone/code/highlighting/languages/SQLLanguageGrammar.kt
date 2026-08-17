// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.code.highlighting.languages

import org.jetbrains.jewel.intui.standalone.code.highlighting.LanguageGrammar
import org.jetbrains.jewel.intui.standalone.code.highlighting.TokenRule
import org.jetbrains.jewel.intui.standalone.code.highlighting.TokenType

// Patterns ported from plugins/textmate/lib/bundles/sql/syntaxes/sql.tmLanguage.json.
//
// Differences you can see:
//  - Nested block comments end early: `/* a /* b */ c */` stops coloring at the first `*/`, where TextMate
//    nests them. java.util.regex cannot recurse.
//  - storage.modifier (`primary key`, `references`, `default`) has no TokenType of its own, so it is
//    colored as a keyword.
//
// Three things that look like bugs but are the bundle's. Its only standalone number rule is `\b\d+\b`, so
// `3.14` is two numbers and `1e10` is not a number at all. It has no constant.language rule, so `null` is a
// keyword and `true`/`false` are not colored. And the `@name` and `[name]` rules deliberately map no
// captures: they still consume their match, which is what keeps `[select]` off the keyword rule.

// storage.type.sql on the type names, constant.numeric.sql on the length and precision arguments. Kept in the
// bundle's own (?xi) extended mode, comments included.
private const val STORAGE_TYPES =
    """(?xi)

        # normal stuff, capture 1
        \b(bigint|bigserial|bit|boolean|box|bytea|cidr|circle|date|double\sprecision|inet|int|integer|
        line|lseg|macaddr|money|oid|path|point|polygon|real|serial|smallint|sysdate|text)\b

        # numeric suffix, capture 2 + 3i
        |\b(bit\svarying|character\s(?:varying)?|tinyint|var\schar|float|interval)\((\d+)\)

        # optional numeric suffix, capture 4 + 5i
        |\b(char|number|varchar\d?)\b(?:\((\d+)\))?

        # special case, capture 6 + 7i + 8i
        |\b(numeric|decimal)\b(?:\((\d+),(\d+)\))?

        # special case, captures 9, 10i, 11
        |\b(times?)\b(?:\((\d+)\))?(\swith(?:out)?\stime\szone\b)?

        # special case, captures 12, 13, 14i, 15
        |\b(timestamp)(?:(s|tz))?\b(?:\((\d+)\))?(\s(with|without)\stime\szone\b)?
    """

// keyword.other.sql, the bundle's catch-all keyword list. `create(\\s+or\\s+alter)?` is double-escaped in the
// JSON source, so the optional group is a literal backslash followed by `s+or...` and can never match real SQL;
// it is copied as-is rather than repaired.
private const val OTHER_KEYWORDS =
    "\\b(?i)(abort|abort_after_wait|absent|absolute|accent_sensitivity|acceptable_cursopt|acp|action|" +
        "activation|add|address|admin|aes_128|aes_192|aes_256|affinity|after|aggregate|algorithm|" +
        "all_constraints|all_errormsgs|all_indexes|all_levels|all_results|allow_connections|allow_dup_row|" +
        "allow_encrypted_value_modifications|allow_page_locks|allow_row_locks|allow_snapshot_isolation|alter|" +
        "altercolumn|always|anonymous|ansi_defaults|ansi_null_default|ansi_null_dflt_off|ansi_null_dflt_on|" +
        "ansi_nulls|ansi_padding|ansi_warnings|appdomain|append|application|apply|arithabort|arithignore|" +
        "array|assembly|asymmetric|asynchronous_commit|at|atan2|atomic|attach|attach_force_rebuild_log|" +
        "attach_rebuild_log|audit|auth_realm|authentication|auto|auto_cleanup|auto_close|" +
        "auto_create_statistics|auto_drop|auto_shrink|auto_update_statistics|auto_update_statistics_async|" +
        "automated_backup_preference|automatic|autopilot|availability|availability_mode|backup|" +
        "backup_priority|base64|basic|batches|batchsize|before|between|bigint|binary|binding|bit|block|" +
        "blockers|blocksize|bmk|both|break|broker|broker_instance|bucket_count|buffer|buffercount|" +
        "bulk_logged|by|call|caller|card|case|catalog|catch|cert|certificate|change_retention|" +
        "change_tracking|change_tracking_context|changes|char|character|character_set|check_expiration|" +
        "check_policy|checkconstraints|checkindex|checkpoint|checksum|cleanup_policy|clear|clear_port|close|" +
        "clustered|codepage|collection|column_encryption_key|column_master_key|columnstore|" +
        "columnstore_archive|colv_80_to_100|colv_100_to_80|commit_differential_base|committed|" +
        "compatibility_level|compress_all_row_groups|compression|compression_delay|concat_null_yields_null|" +
        "concatenate|configuration|connect|connection|containment|continue|continue_after_error|contract|" +
        "contract_name|control|conversation|conversation_group_id|conversation_handle|copy|copy_only|" +
        "count_rows|counter|create(\\\\s+or\\\\s+alter)?|credential|cross|cryptographic|" +
        "cryptographic_provider|cube|cursor|cursor_close_on_commit|cursor_default|data|data_compression|" +
        "data_flush_interval_seconds|data_mirroring|data_purity|data_source|database|database_name|" +
        "database_snapshot|datafiletype|date_correlation_optimization|date|datefirst|dateformat|date_format|" +
        "datetime|datetime2|datetimeoffset|day(s)?|db_chaining|dbid|dbidexec|dbo_only|deadlock_priority|" +
        "deallocate|dec|decimal|declare|decrypt|decrypt_a|decryption|default_database|" +
        "default_fulltext_language|default_language|default_logon_domain|default_schema|definition|delay|" +
        "delayed_durability|delimitedtext|density_vector|dependent|des|description|desired_state|desx|" +
        "differential|digest|disable|disable_broker|disable_def_cnst_chk|disabled|disk|distinct|distributed|" +
        "distribution|drop|drop_existing|dts_buffers|dump|durability|dynamic|edition|elements|else|emergency|" +
        "empty|enable|enable_broker|enabled|encoding|encrypted|encrypted_value|encryption|encryption_type|" +
        "end|endpoint|endpoint_url|enhancedintegrity|entry|error_broker_conversations|errorfile|estimateonly|" +
        "event|except|exec|executable|execute|exists|expand|expiredate|expiry_date|explicit|external|" +
        "external_access|failover|failover_mode|failure_condition_level|fast|fast_forward|fastfirstrow|" +
        "federated_service_account|fetch|field_terminator|fieldterminator|file|filelistonly|filegroup|" +
        "filegrowth|filename|filestream|filestream_log|filestream_on|filetable|file_format|filter|first_row|" +
        "fips_flagger|fire_triggers|first|firstrow|float|flush_interval_seconds|fmtonly|following|for|force|" +
        "force_failover_allow_data_loss|force_service_allow_data_loss|forced|forceplan|formatfile|" +
        "format_options|format_type|formsof|forward_only|free_cursors|free_exec_context|fullscan|fulltext|" +
        "fulltextall|fulltextkey|function|generated|get|geography|geometry|global|go|goto|governor|guid|" +
        "hadoop|hardening|hash|hashed|header_limit|headeronly|health_check_timeout|hidden|hierarchyid|" +
        "histogram|histogram_steps|hits_cursors|hits_exec_context|hour(s)?|http|identity|identity_value|if|" +
        "ifnull|ignore|ignore_constraints|ignore_dup_key|ignore_dup_row|ignore_triggers|image|immediate|" +
        "implicit_transactions|include|include_null_values|incremental|index|inflectional|init|initiator|" +
        "insensitive|insert|instead|int|integer|integrated|intersect|intermediate|interval_length_minutes|" +
        "into|inuse_cursors|inuse_exec_context|io|is|isabout|iso_week|isolation|job_tracker_location|json|" +
        "keep|keep_nulls|keep_replication|keepdefaults|keepfixed|keepidentity|keepnulls|kerberos|key|" +
        "key_path|key_source|key_store_provider_name|keyset|kill|kilobytes_per_batch|labelonly|langid|" +
        "language|last|lastrow|leading|legacy_cardinality_estimation|length|level|lifetime|lineage_80_to_100|" +
        "lineage_100_to_80|listener_ip|listener_port|load|loadhistory|lob_compaction|local|" +
        "local_service_name|locate|location|lock_escalation|lock_timeout|lockres|log|login|login_type|loop|" +
        "manual|mark_in_use_for_removal|masked|master|match|matched|max_queue_readers|max_duration|" +
        "max_outstanding_io_per_volume|maxdop|maxerrors|maxlength|maxtransfersize|max_plans_per_query|" +
        "max_storage_size_mb|mediadescription|medianame|mediapassword|memogroup|memory_optimized|merge|" +
        "message|message_forward_size|message_forwarding|microsecond|millisecond|minute(s)?|mirror_address|" +
        "misses_cursors|misses_exec_context|mixed|modify|money|month|move|multi_user|must_change|name|" +
        "namespace|nanosecond|native|native_compilation|nchar|ncharacter|nested_triggers|never|new_account|" +
        "new_broker|newname|next|no|no_browsetable|no_checksum|no_compression|no_infomsgs|no_triggers|" +
        "no_truncate|nocount|noexec|noexpand|noformat|noinit|nolock|nonatomic|nonclustered|nondurable|none|" +
        "norecompute|norecovery|noreset|norewind|noskip|not|notification|nounload|now|nowait|ntext|ntlm|" +
        "nulls|numeric|numeric_roundabort|nvarchar|object|objid|oem|offline|old_account|online|" +
        "operation_mode|open|openjson|optimistic|option|orc|out|outer|output|over|override|owner|ownership|" +
        "pad_index|page|page_checksum|page_verify|pagecount|paglock|param|parameter_sniffing|" +
        "parameter_type_expansion|parameterization|parquet|parseonly|partial|partition|partner|password|path|" +
        "pause|percentage|permission_set|persisted|period|physical_only|plan_forcing_mode|policy|pool|" +
        "population|ports|preceding|precision|predicate|presume_abort|primary|primary_role|print|prior|" +
        "priority |priority_level|private|proc(edure)?|procedure_name|profile|provider|quarter|" +
        "query_capture_mode|query_governor_cost_limit|query_optimizer_hotfixes|query_store|queue|" +
        "quoted_identifier|raiserror|range|raw|rcfile|rc2|rc4|rc4_128|rdbms|read_committed_snapshot|read|" +
        "read_only|read_write|readcommitted|readcommittedlock|readonly|readpast|readuncommitted|readwrite|" +
        "real|rebuild|receive|recmodel_70backcomp|recompile|reconfigure|recovery|recursive|" +
        "recursive_triggers|redo_queue|reject_sample_value|reject_type|reject_value|relative|remote|" +
        "remote_data_archive|remote_proc_transactions|remote_service_name|remove|removed_cursors|" +
        "removed_exec_context|reorganize|repeat|repeatable|repeatableread|replace|replica|replicated|" +
        "replnick_100_to_80|replnickarray_80_to_100|replnickarray_100_to_80|required|required_cursopt|" +
        "resample|reset|resource|resource_manager_location|respect|restart|restore|restricted_user|resume|" +
        "retaindays|retention|return|revert|rewind|rewindonly|returns|robust|role|rollup|root|round_robin|" +
        "route|row|rowdump|rowguidcol|rowlock|row_terminator|rows|rows_per_batch|rowsets_only|rowterminator|" +
        "rowversion|rsa_1024|rsa_2048|rsa_3072|rsa_4096|rsa_512|safe|safety|sample|save|scalar|schema|" +
        "schemabinding|scoped|scroll|scroll_locks|sddl|second|secexpr|seconds|secondary|secondary_only|" +
        "secondary_role|secret|security|securityaudit|selective|self|send|sent|sequence|serde_method|" +
        "serializable|server|service|service_broker|service_name|service_objective|session_timeout|session|" +
        "sessions|seterror|setopts|sets|shard_map_manager|shard_map_name|sharded|shared_memory|shortest_path|" +
        "show_statistics|showplan_all|showplan_text|showplan_xml|showplan_xml_with_recompile|shrinkdb|" +
        "shutdown|sid|signature|simple|single_blob|single_clob|single_nclob|single_user|singleton|site|size|" +
        "size_based_cleanup_mode|skip|smalldatetime|smallint|smallmoney|snapshot|snapshot_import|" +
        "snapshotrestorephase|soap|softnuma|sort_in_tempdb|sorted_data|sorted_data_reorg|spatial|sql|" +
        "sql_bigint|sql_binary|sql_bit|sql_char|sql_date|sql_decimal|sql_double|sql_float|sql_guid|" +
        "sql_handle|sql_longvarbinary|sql_longvarchar|sql_numeric|sql_real|sql_smallint|sql_time|" +
        "sql_timestamp|sql_tinyint|sql_tsi_day|sql_tsi_frac_second|sql_tsi_hour|sql_tsi_minute|sql_tsi_month|" +
        "sql_tsi_quarter|sql_tsi_second|sql_tsi_week|sql_tsi_year|sql_type_date|sql_type_time|" +
        "sql_type_timestamp|sql_varbinary|sql_varchar|sql_variant|sql_wchar|sql_wlongvarchar|ssl|ssl_port|" +
        "standard|standby|start|start_date|started|stat_header|state|statement|static|statistics|" +
        "statistics_incremental|statistics_norecompute|statistics_only|statman|stats|stats_stream|status|" +
        "stop|stop_on_error|stopat|stopatmark|stopbeforemark|stoplist|stopped|string_delimiter|subject|" +
        "supplemental_logging|supported|suspend|symmetric|synchronous_commit|synonym|sysname|system|" +
        "system_time|system_versioning|table|tableresults|tablock|tablockx|take|tape|target|target_index|" +
        "target_partition|target_recovery_time|tcp|temporal_history_retention|text|textimage_on|then|" +
        "thesaurus|throw|time|timeout|timestamp|tinyint|to|top|torn_page_detection|track_columns_updated|" +
        "trailing|tran|transaction|transfer|transform_noise_words|triple_des|triple_des_3key|truncate|" +
        "trustworthy|try|tsql|two_digit_year_cutoff|type|type_desc|type_warning|tzoffset|uid|unbounded|" +
        "uncommitted|unique|uniqueidentifier|unlimited|unload|unlock|unsafe|updlock|url|use|useplan|" +
        "useroptions|use_type_default|using|utcdatetime|valid_xml|validation|value|values|varbinary|varchar|" +
        "vector|verbose|verifyonly|version|view_metadata|virtual_device|visiblity|wait_at_low_priority|" +
        "waitfor|webmethod|week|weekday|weight|well_formed_xml|when|while|widechar|widechar_ansi|widenative|" +
        "window|windows|with|within|within group|witness|without|without_array_wrapper|workload|wsdl|" +
        "xact_abort|xlock|xml|xmlschema|xquery|xsinil|year|zone)\\b"

internal val SQL =
    LanguageGrammar(
        name = "sql",
        aliases = listOf("cql", "db2", "ddl", "dml", "dsql", "inc", "mysql", "prc", "sql", "tab", "udf", "viw"),
        rules =
            listOf(
                // text.variable and text.bracketed: matched and consumed, but not colored — see the header.
                TokenRule("((?<!@)@)\\b(\\w+)\\b", emptyMap()),
                TokenRule("(\\[)[^\\]]*(\\])", emptyMap()),
                // #comments — comment.line.double-dash.sql, then #comment-block's comment.block
                TokenRule.comment("--[^\\r\\n]*+"),
                TokenRule.comment("/\\*[\\s\\S]*?\\*/"),
                // meta.create.sql: keyword.other.create.sql, keyword.other.sql, entity.name.function.sql
                TokenRule(
                    "(?m)(?i:^\\s*(create(?:\\s+or\\s+replace)?)\\s+(aggregate|conversion|database|domain|" +
                        "function|group|(unique\\s+)?index|language|operator class|operator|rule|schema|" +
                        "sequence|table|tablespace|trigger|type|user|view)\\s+)(['\"`]?)(\\w+)\\4",
                    mapOf(1 to TokenType.KEYWORD, 2 to TokenType.KEYWORD, 5 to TokenType.FUNCTION_CALL),
                ),
                // meta.drop.sql: keyword.other.create.sql, keyword.other.sql
                TokenRule(
                    "(?m)(?i:^\\s*(drop)\\s+(aggregate|conversion|database|domain|function|group|index|" +
                        "language|operator class|operator|rule|schema|sequence|table|tablespace|trigger|type|" +
                        "user|view))",
                    mapOf(1 to TokenType.KEYWORD, 2 to TokenType.KEYWORD),
                ),
                // meta.drop.sql: keyword.other.create.sql, keyword.other.table.sql, entity.name.function.sql,
                // keyword.other.cascade.sql
                TokenRule(
                    "(?i:\\s*(drop)\\s+(table)\\s+(\\w+)(\\s+cascade)?\\b)",
                    mapOf(
                        1 to TokenType.KEYWORD,
                        2 to TokenType.KEYWORD,
                        3 to TokenType.FUNCTION_CALL,
                        4 to TokenType.KEYWORD,
                    ),
                ),
                // meta.alter.sql: keyword.other.create.sql, keyword.other.table.sql
                TokenRule(
                    "(?m)(?i:^\\s*(alter)\\s+(aggregate|conversion|database|domain|function|group|index|" +
                        "language|operator class|operator|proc(edure)?|rule|schema|sequence|table|tablespace|" +
                        "trigger|type|user|view)\\s+)",
                    mapOf(1 to TokenType.KEYWORD, 2 to TokenType.KEYWORD),
                ),
                // storage.type.sql + constant.numeric.sql. storage.type is IntelliJ's keyword key.
                TokenRule(
                    STORAGE_TYPES,
                    mapOf(
                        1 to TokenType.KEYWORD,
                        2 to TokenType.KEYWORD,
                        3 to TokenType.NUMBER,
                        4 to TokenType.KEYWORD,
                        5 to TokenType.NUMBER,
                        6 to TokenType.KEYWORD,
                        7 to TokenType.NUMBER,
                        8 to TokenType.NUMBER,
                        9 to TokenType.KEYWORD,
                        10 to TokenType.NUMBER,
                        11 to TokenType.KEYWORD,
                        12 to TokenType.KEYWORD,
                        13 to TokenType.KEYWORD,
                        14 to TokenType.NUMBER,
                        15 to TokenType.KEYWORD,
                    ),
                ),
                // storage.modifier.sql — KEYWORD is our choice, there is no TokenType for storage.modifier
                TokenRule.keyword(
                    "(?i:\\b((?:primary|foreign)\\s+key|references|on\\s+(delete|update)(\\s+cascade)?|" +
                        "nocheck|check|constraint|collate|default)\\b)"
                ),
                // constant.numeric.sql — integers only; the bundle has no float or exponent rule
                TokenRule.number("\\b\\d+\\b"),
                // keyword.other.DML.sql
                TokenRule.keyword(
                    "(?i:\\b(select(\\s+(all|distinct))?|insert\\s+(ignore\\s+)?into|update|delete|from|set|" +
                        "where|group\\s+by|or|like|and|union(\\s+all)?|having|order\\s+by|limit|cross\\s+join|" +
                        "join|straight_join|(inner|(left|right|full)(\\s+outer)?)\\s+join|" +
                        "natural(\\s+(inner|(left|right|full)(\\s+outer)?))?\\s+join)\\b)"
                ),
                // keyword.other.DDL.create.II.sql — this is where `null` is handled
                TokenRule.keyword("(?i:\\b(on|off|((is\\s+)?not\\s+)?null)\\b)"),
                // keyword.other.DML.II.sql
                TokenRule.keyword("(?i:\\bvalues\\b)"),
                // keyword.other.LUW.sql
                TokenRule.keyword(
                    "(?i:\\b(begin(\\s+work)?|start\\s+transaction|commit(\\s+work)?|rollback(\\s+work)?)\\b)"
                ),
                // keyword.other.authorization.sql
                TokenRule.keyword("(?i:\\b(grant(\\swith\\sgrant\\soption)?|revoke)\\b)"),
                // keyword.other.data-integrity.sql
                TokenRule.keyword("(?i:\\bin\\b)"),
                // keyword.other.object-comments.sql
                TokenRule.keyword(
                    "(?m)(?i:^\\s*(comment\\s+on\\s+(table|column|aggregate|constraint|database|domain|" +
                        "function|index|operator|rule|schema|sequence|trigger|type|view))\\s+)"
                ),
                // keyword.other.alias.sql
                TokenRule.keyword("(?i)\\bAS\\b"),
                // keyword.other.order.sql
                TokenRule.keyword("(?i)\\b(DESC|ASC)\\b"),
                // keyword.operator.star.sql, .comparison.sql, .math.sql, .concatenator.sql
                TokenRule.operator("\\*"),
                TokenRule.operator("[!<>]?=|<>|<|>"),
                TokenRule.operator("-|\\+|/"),
                TokenRule.operator("\\|\\|"),
                // support.function.aggregate.sql
                TokenRule.functionCall(
                    "(?i)\\b(approx_count_distinct|approx_percentile_cont|approx_percentile_disc|avg|" +
                        "checksum_agg|count|count_big|group|grouping|grouping_id|max|min|sum|stdev|stdevp|var|" +
                        "varp)\\b\\s*\\("
                ),
                // support.function.analytic.sql
                TokenRule.functionCall(
                    "(?i)\\b(cume_dist|first_value|lag|last_value|lead|percent_rank|percentile_cont|" +
                        "percentile_disc)\\b\\s*\\("
                ),
                // support.function.bitmanipulation.sql
                TokenRule.functionCall("(?i)\\b(bit_count|get_bit|left_shift|right_shift|set_bit)\\b\\s*\\("),
                // support.function.conversion.sql
                TokenRule.functionCall("(?i)\\b(cast|convert|parse|try_cast|try_convert|try_parse)\\b\\s*\\("),
                // support.function.collation.sql
                TokenRule.functionCall("(?i)\\b(collationproperty|tertiary_weights)\\b\\s*\\("),
                // support.function.cryptographic.sql
                TokenRule.functionCall(
                    "(?i)\\b(asymkey_id|asymkeyproperty|certproperty|cert_id|crypt_gen_random|" +
                        "decryptbyasymkey|decryptbycert|decryptbykey|decryptbykeyautoasymkey|" +
                        "decryptbykeyautocert|decryptbypassphrase|encryptbyasymkey|encryptbycert|encryptbykey|" +
                        "encryptbypassphrase|hashbytes|is_objectsigned|key_guid|key_id|key_name|" +
                        "signbyasymkey|signbycert|symkeyproperty|verifysignedbycert|" +
                        "verifysignedbyasymkey)\\b\\s*\\("
                ),
                // support.function.cursor.sql
                TokenRule.functionCall("(?i)\\b(cursor_status)\\b\\s*\\("),
                // support.function.datetime.sql
                TokenRule.functionCall(
                    "(?i)\\b(sysdatetime|sysdatetimeoffset|sysutcdatetime|current_time(stamp)?|getdate|" +
                        "getutcdate|datename|datepart|day|month|year|datefromparts|datetime2fromparts|" +
                        "datetimefromparts|datetimeoffsetfromparts|smalldatetimefromparts|timefromparts|" +
                        "datediff|dateadd|datetrunc|eomonth|switchoffset|todatetimeoffset|isdate|" +
                        "date_bucket)\\b\\s*\\("
                ),
                // support.function.datatype.sql
                TokenRule.functionCall(
                    "(?i)\\b(datalength|ident_current|ident_incr|ident_seed|identity|" +
                        "sql_variant_property)\\b\\s*\\("
                ),
                // support.function.expression.sql
                TokenRule.functionCall("(?i)\\b(coalesce|nullif)\\b\\s*\\("),
                // support.function.globalvar.sql
                TokenRule.functionCall(
                    "(?<!@)@@(?i)\\b(cursor_rows|connections|cpu_busy|datefirst|dbts|error|fetch_status|" +
                        "identity|idle|io_busy|langid|language|lock_timeout|max_connections|max_precision|" +
                        "nestlevel|options|packet_errors|pack_received|pack_sent|procid|remserver|rowcount|" +
                        "servername|servicename|spid|textsize|timeticks|total_errors|total_read|total_write|" +
                        "trancount|version)\\b\\s*\\("
                ),
                // support.function.json.sql
                TokenRule.functionCall(
                    "(?i)\\b(json|isjson|json_object|json_array|json_value|json_query|json_modify|" +
                        "json_path_exists)\\b\\s*\\("
                ),
                // support.function.logical.sql
                TokenRule.functionCall("(?i)\\b(choose|iif|greatest|least)\\b\\s*\\("),
                // support.function.mathematical.sql
                TokenRule.functionCall(
                    "(?i)\\b(abs|acos|asin|atan|atn2|ceiling|cos|cot|degrees|exp|floor|log|log10|pi|power|" +
                        "radians|rand|round|sign|sin|sqrt|square|tan)\\b\\s*\\("
                ),
                // support.function.metadata.sql
                TokenRule.functionCall(
                    "(?i)\\b(app_name|applock_mode|applock_test|assemblyproperty|col_length|col_name|" +
                        "columnproperty|database_principal_id|databasepropertyex|db_id|db_name|file_id|" +
                        "file_idex|file_name|filegroup_id|filegroup_name|filegroupproperty|fileproperty|" +
                        "fulltextcatalogproperty|fulltextserviceproperty|index_col|indexkey_property|" +
                        "indexproperty|object_definition|object_id|object_name|object_schema_name|" +
                        "objectproperty|objectpropertyex|original_db_name|parsename|schema_id|schema_name|" +
                        "scope_identity|serverproperty|stats_date|type_id|type_name|typeproperty)\\b\\s*\\("
                ),
                // support.function.ranking.sql
                TokenRule.functionCall("(?i)\\b(rank|dense_rank|ntile|row_number)\\b\\s*\\("),
                // support.function.rowset.sql
                TokenRule.functionCall(
                    "(?i)\\b(generate_series|opendatasource|openjson|openrowset|openquery|openxml|predict|" +
                        "string_split)\\b\\s*\\("
                ),
                // support.function.security.sql
                TokenRule.functionCall(
                    "(?i)\\b(certencoded|certprivatekey|current_user|database_principal_id|" +
                        "has_perms_by_name|is_member|is_rolemember|is_srvrolemember|original_login|" +
                        "permissions|pwdcompare|pwdencrypt|schema_id|schema_name|session_user|suser_id|" +
                        "suser_sid|suser_sname|system_user|suser_name|user_id|user_name)\\b\\s*\\("
                ),
                // support.function.string.sql
                TokenRule.functionCall(
                    "(?i)\\b(ascii|char|charindex|concat|difference|format|left|len|lower|ltrim|nchar|nodes|" +
                        "patindex|quotename|replace|replicate|reverse|right|rtrim|soundex|space|str|" +
                        "string_agg|string_escape|string_split|stuff|substring|translate|trim|unicode|" +
                        "upper)\\b\\s*\\("
                ),
                // support.function.system.sql
                TokenRule.functionCall(
                    "(?i)\\b(binary_checksum|checksum|compress|connectionproperty|context_info|" +
                        "current_request_id|current_transaction_id|decompress|error_line|error_message|" +
                        "error_number|error_procedure|error_severity|error_state|formatmessage|" +
                        "get_filestream_transaction_context|getansinull|host_id|host_name|isnull|isnumeric|" +
                        "min_active_rowversion|newid|newsequentialid|rowcount_big|session_context|session_id|" +
                        "xact_state)\\b\\s*\\("
                ),
                // support.function.textimage.sql
                TokenRule.functionCall("(?i)\\b(patindex|textptr|textvalid)\\b\\s*\\("),
                // support.function.vector.sql
                TokenRule.functionCall("(?i)\\b(vector_distance|vector_norm|vector_normalize)\\b\\s*\\("),
                // constant.other.database-name.sql and constant.other.table-name.sql
                TokenRule("(\\w+?)\\.(\\w+)", mapOf(1 to TokenType.CONSTANT, 2 to TokenType.CONSTANT)),
                // #strings — string.quoted.single.sql, the fast path first and the begin/end fallback second,
                // exactly as the bundle orders them; then the same pair for backticks and double quotes, then
                // string.other.quoted.brackets.sql
                TokenRule.string("(?:(?<![a-zA-Z0-9_])(N))?(')[^']*(')"),
                TokenRule.string("'(?:\\\\.|[^'])*+'"),
                TokenRule.string("(`)[^`\\\\]*(`)"),
                TokenRule.string("`(?:\\\\.|[^`])*+`"),
                TokenRule.string("(\")[^\"#]*(\")"),
                TokenRule.string("\"[^\"]*+\""),
                TokenRule.string("%\\{[^}]*+\\}"),
                // #regexps — string.regexp.sql and string.regexp.modr.sql. These fire far less often than they
                // look like they would: keyword.operator.math.sql above matches `/` at the same offset and is
                // listed first, so it wins the tie, in this engine as in TextMate.
                TokenRule.string("/(?=\\S.*/)(?:\\\\/|[^/])*+/"),
                TokenRule.string("%r\\{[^}]*+\\}"),
                // keyword.other.sql
                TokenRule.keyword(OTHER_KEYWORDS),
            ),
    )
