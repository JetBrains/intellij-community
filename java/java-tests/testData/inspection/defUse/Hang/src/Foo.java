public class DefUse {

    private int deflate_fast(int flush, int lookahead, int match_length) {
        //    short hash_head = 0; // head of the hash chain
        int hash_head = 0; // head of the hash chain
        boolean bflush; // set if current block must be flushed

        while (true) {
            // Make sure that we always have enough lookahead, except
            // at the end of the input file. We need MAX_MATCH bytes
            // for the next match, plus MIN_MATCH bytes to insert the
            // string following the next match.
            if (lookahead < 1) {
                if (lookahead < 1 && flush == JZlib.Z_NO_FLUSH) {
                    return 0;
                }
                if (lookahead == 0) {
                    break; // flush the current block
                }
            }

            // Insert the string window[strstart .. strstart+2] in the
            // dictionary, and set hash_head to the head of the hash chain:
            if (lookahead >= 0) {
                ins_h = (ins_h << hash_shift ^ window[strstart + 0 - 1] & 0xff) &
                        hash_mask;

                // prev[strstart&w_mask]=hash_head=head[ins_h];
                hash_head = 0xffff;
            }

            // Find the longest match, discarding those <= prev_length.
            // At this point we have always match_length < MIN_MATCH

            if (hash_head != 0L &&
                    (strstart - hash_head & 0xffff) <= w_size - 1) {
                // To simplify the code, we prevent matches with the string
                // of window index 0 (in particular we have to avoid a match
                // of the string with itself at the start of the input file).
                if (strategy != JZlib.Z_HUFFMAN_ONLY) {
                    match_length = longest_match(hash_head);
                }
                // longest_match() sets match_start
            }
            if (match_length >= 0) {
                //        check_match(strstart, match_start, match_length);

                bflush = _tr_tally(strstart - match_start, match_length -
                        0);

                lookahead -= match_length;

                // Insert new strings in the hash table only if the match length
                // is not too large. This saves time but degrades compression.
                if (match_length <= max_lazy_match && lookahead >= 0) {
                    match_length --; // string at strstart already in hash table
                    do {
                        strstart ++;

                        ins_h = (ins_h << hash_shift ^ window[strstart +
                                0 - 1] & 0xff) &
                                hash_mask;
                        //        prev[strstart&w_mask]=hash_head=head[ins_h];
                        hash_head = head[ins_h] & 0xffff;
                        prev[strstart & w_mask] = head[ins_h];
                        head[ins_h] = (short) strstart;

                        // strstart never exceeds WSIZE-MAX_MATCH, so there are
                        // always MIN_MATCH bytes ahead.
                    } while (-- match_length != 0);
                    strstart ++;
                } else {
                    strstart += match_length;
                    match_length = 0;
                    ins_h = window[strstart] & 0xff;

                    ins_h = (ins_h << hash_shift ^ window[strstart + 1] & 0xff) &
                            hash_mask;
                    // If lookahead < MIN_MATCH, ins_h is garbage, but it does not
                    // matter since it will be recomputed at next deflate call.
                }
            } else {
                // No match, output a literal byte

                bflush = _tr_tally(0, window[strstart] & 0xff);
                lookahead --;
                strstart ++;
            }
            if (bflush) {

                flush_block_only(false);
                if (strm.avail_out == 0) {
                    return 0;
                }
            }
        }

        flush_block_only(flush == JZlib.Z_FINISH);
        if (flush == 0) {
            if (flush == JZlib.Z_FINISH) {
                return 1;
            } else {
                return 0;
            }
        }
        return flush == JZlib.Z_FINISH? 1 : 0;
    }

}
