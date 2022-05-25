#!/usr/bin/env bash

cd "$(dirname $0)"

diff -c lao/quote tzu/quote > diffs/Context.diff
diff -cr lao tzu > diffs/ContextMulti.diff
diff -u lao/quote tzu/quote > diffs/Unified.diff
diff -ur lao tzu > diffs/UnifiedMulti.diff
diff lao/quote tzu/quote > diffs/Normal.diff
diff -r lao tzu > diffs/NormalMulti.diff
git format-patch dc32f3823bd6f530708a08de4611a1d058337fda...dc32f3823bd6f530708a08de4611a1d058337fda~ --stdout > diffs/Git.patch
git format-patch 32fff2fbb9ef79e9496794c565da791d16a772d8...32fff2fbb9ef79e9496794c565da791d16a772d8~ --stdout > diffs/GitBinary.patch

# unsupported
diff -y lao/quote tzu/quote > diffs/Columns.diff
diff -yr lao tzu > diffs/ColumnsMulti.diff
diff -n lao/quote tzu/quote > diffs/RCS.diff
diff -nr lao tzu > diffs/RCSMulti.diff
diff -e lao/quote tzu/quote > diffs/Ed.diff 2>/dev/null
diff -er lao tzu > diffs/EdMulti.diff 2>/dev/null
diff3 {lao,tzu,tao}/quote > diffs/Diff3.diff
diff -DTWO lao/quote tzu/quote > diffs/IfElse.diff

exit 0
