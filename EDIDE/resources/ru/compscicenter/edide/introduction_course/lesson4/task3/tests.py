from test_helper import run_common_tests

if __name__ == '__main__':
    run_common_tests('''animals = ['elephant', 'lion', 'tiger', "giraffe", "monkey", 'dog']
print(animals)

animals[1:3] = ['cat']
print(animals)

animals[1:3] = []
print(animals)

clear list
print(animals)''',
                     '''animals = ['elephant', 'lion', 'tiger', "giraffe", "monkey", 'dog']
print(animals)

animals[1:3] = ['cat']
print(animals)

animals[1:3] = []
print(animals)


print(animals)''', "Use assignment of empty list")
