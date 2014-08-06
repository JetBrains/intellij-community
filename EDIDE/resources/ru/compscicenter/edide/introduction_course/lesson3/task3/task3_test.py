from test_helper import run_common_tests, import_file


def test_value(path):
    file = import_file(path)
    if file.p_letter == "P":
        return "Bravo"
    return "String index starts with 0."


if __name__ == '__main__':
    run_common_tests('''python = "Python"
p_letter = type here
print(p_letter)
''', '''python = "Python"
p_letter =
print(p_letter)
''', "You should modify the file")

    # TODO: get filepath. Let's now assume that we pass it as the last item in command-line
    import sys
    path = sys.argv[-1]
    print(test_value(path))