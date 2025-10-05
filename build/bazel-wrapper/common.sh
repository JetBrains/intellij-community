# Common code for all Bazel wrappers

# output_user_root_opt will be used upon running Bazel
# IJI-2767 Bazel: move bazel output root to a standard location
# shellcheck disable=SC2034
case "$(uname -s)" in
  Darwin) output_user_root_opt=--output_user_root=~/Library/Caches/JetBrains/MonorepoBazel ;;
  Linux)  output_user_root_opt=--output_user_root=~/.cache/JetBrains/MonorepoBazel ;;
  *)      echo "unrecognized operating system $(uname -s)"; exit 1 ;;
esac
